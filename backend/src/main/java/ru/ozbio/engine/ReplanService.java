package ru.ozbio.engine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import ru.ozbio.engine.exception.NoMachineAvailableException;
import ru.ozbio.engine.model.OrderOperationKey;
import ru.ozbio.engine.model.PlannedScheduleItem;
import ru.ozbio.engine.model.PlanningBatch;
import ru.ozbio.persistence.DetailRepository;
import ru.ozbio.persistence.MachineRepository;
import ru.ozbio.persistence.OrderRepository;
import ru.ozbio.persistence.ToolRepository;
import ru.ozbio.service.model.MachineSummary;
import ru.ozbio.service.model.OperationLine;
import ru.ozbio.service.model.OrderDetailLine;
import ru.ozbio.service.model.OrderSummary;
import ru.ozbio.service.model.OrderToolLine;
import ru.ozbio.service.model.ToolDetailLine;

@Service
public class ReplanService {

    private final OrderRepository orderRepository;
    private final ToolRepository toolRepository;
    private final DetailRepository detailRepository;
    private final MachineRepository machineRepository;

    public ReplanService(
            OrderRepository orderRepository,
            ToolRepository toolRepository,
            DetailRepository detailRepository,
            MachineRepository machineRepository) {
        this.orderRepository = orderRepository;
        this.toolRepository = toolRepository;
        this.detailRepository = detailRepository;
        this.machineRepository = machineRepository;
    }

    public List<PlannedScheduleItem> replan() {
        return replan(Instant.now());
    }

    List<PlannedScheduleItem> replan(Instant planningStart) {
        List<PlanningBatch> batches = calculateOperationsForPlanning();
        if (batches.isEmpty()) {
            return List.of();
        }

        Set<Long> operationIds =
                batches.stream().map(PlanningBatch::operationId).collect(Collectors.toSet());
        Map<Long, OperationLine> operationsById = detailRepository.findOperationsByIds(operationIds);

        Set<Long> machineTypeIds =
                operationsById.values().stream()
                        .map(OperationLine::machineTypeId)
                        .collect(Collectors.toSet());
        Map<Long, List<MachineSummary>> machinesByTypeId =
                machineRepository.findMachinesByMachineTypeIds(machineTypeIds);

        Map<Long, Instant> machineFreeAt = new HashMap<>();
        Map<OrderOperationKey, Instant> batchEndAt = new HashMap<>();
        List<PlannedScheduleItem> schedule = new ArrayList<>();

        for (PlanningBatch batch : batches) {
            OperationLine operation = operationsById.get(batch.operationId());
            if (operation == null) {
                throw new IllegalStateException("Operation not found: " + batch.operationId());
            }

            List<MachineSummary> machines =
                    machinesByTypeId.getOrDefault(operation.machineTypeId(), List.of());
            if (machines.isEmpty()) {
                throw new NoMachineAvailableException(operation.machineTypeId(), operation.id());
            }

            MachineSummary machine = pickEarliestFreeMachine(machines, machineFreeAt, planningStart);

            Instant materialReady = resolveMaterialReady(batch, batchEndAt, planningStart);
            Instant machineReady = machineFreeAt.getOrDefault(machine.id(), planningStart);
            Instant start = max(planningStart, machineReady, materialReady);
            Instant end =
                    start.plus(
                            operation
                                    .setupDuration()
                                    .plus(operation.duration().multipliedBy(batch.count())));

            schedule.add(
                    new PlannedScheduleItem(
                            batch.orderId(),
                            batch.operationId(),
                            machine.id(),
                            batch.count(),
                            start,
                            end));

            machineFreeAt.put(machine.id(), end);
            batchEndAt.put(new OrderOperationKey(batch.orderId(), batch.operationId()), end);
        }

        return schedule;
    }

    public List<PlanningBatch> calculateOperationsForPlanning() {
        List<OrderSummary> orders = orderRepository.findAllForPlanning();
        if (orders.isEmpty()) {
            return List.of();
        }

        List<Long> orderIds = orders.stream().map(OrderSummary::id).toList();
        Map<Long, List<OrderDetailLine>> detailsByOrderId = orderRepository.findDetailsByOrderIds(orderIds);
        Map<Long, List<OrderToolLine>> toolsByOrderId = orderRepository.findToolsByOrderIds(orderIds);

        Set<Long> toolIds = new HashSet<>();
        for (List<OrderToolLine> toolLines : toolsByOrderId.values()) {
            for (OrderToolLine toolLine : toolLines) {
                toolIds.add(toolLine.toolId());
            }
        }
        Map<Long, List<ToolDetailLine>> toolDetailsByToolId = toolRepository.findDetailsByToolIds(toolIds);

        Set<Long> detailIds = new HashSet<>();
        List<Map<Long, Integer>> demandPerOrder = new ArrayList<>();
        for (OrderSummary order : orders) {
            Map<Long, Integer> demand =
                    collectDetailDemand(
                            order.id(),
                            detailsByOrderId,
                            toolsByOrderId,
                            toolDetailsByToolId);
            demandPerOrder.add(demand);
            detailIds.addAll(demand.keySet());
        }

        Map<Long, List<OperationLine>> operationsByDetailId =
                detailRepository.findOperationsByDetailIds(detailIds);

        List<PlanningBatch> batches = new ArrayList<>();
        for (int i = 0; i < orders.size(); i++) {
            OrderSummary order = orders.get(i);
            Map<Long, Integer> demand = demandPerOrder.get(i);

            // TODO: приоритет деталей внутри заказа при формировании батчей
            for (var entry : demand.entrySet()) {
                long detailId = entry.getKey();
                int quantity = entry.getValue();

                Long previousOperationId = null;
                for (OperationLine operation :
                        operationsByDetailId.getOrDefault(detailId, List.of())) {
                    // TODO: учитывать остатки на storage при расчёте count
                    batches.add(
                            new PlanningBatch(
                                    order.id(), operation.id(), quantity, previousOperationId));
                    previousOperationId = operation.id();
                }
            }
        }

        return batches;
    }

    private static Instant resolveMaterialReady(
            PlanningBatch batch,
            Map<OrderOperationKey, Instant> batchEndAt,
            Instant planningStart) {
        if (batch.previousOperationId() == null) {
            return planningStart;
        }
        OrderOperationKey key =
                new OrderOperationKey(batch.orderId(), batch.previousOperationId());
        Instant predecessorEnd = batchEndAt.get(key);
        if (predecessorEnd == null) {
            throw new IllegalStateException(
                    "Previous operation not scheduled: order "
                            + batch.orderId()
                            + ", operation "
                            + batch.previousOperationId());
        }
        return predecessorEnd;
    }

    private static MachineSummary pickEarliestFreeMachine(
            List<MachineSummary> machines, Map<Long, Instant> machineFreeAt, Instant planningStart) {
        MachineSummary best = machines.getFirst();
        Instant bestFreeAt = machineFreeAt.getOrDefault(best.id(), planningStart);

        for (int i = 1; i < machines.size(); i++) {
            MachineSummary candidate = machines.get(i);
            Instant candidateFreeAt = machineFreeAt.getOrDefault(candidate.id(), planningStart);
            if (candidateFreeAt.isBefore(bestFreeAt)) {
                best = candidate;
                bestFreeAt = candidateFreeAt;
            }
        }

        return best;
    }

    private static Instant max(Instant first, Instant second, Instant third) {
        Instant result = first.isAfter(second) ? first : second;
        return result.isAfter(third) ? result : third;
    }

    private Map<Long, Integer> collectDetailDemand(
            long orderId,
            Map<Long, List<OrderDetailLine>> detailsByOrderId,
            Map<Long, List<OrderToolLine>> toolsByOrderId,
            Map<Long, List<ToolDetailLine>> toolDetailsByToolId) {
        Map<Long, Integer> demand = new LinkedHashMap<>();

        for (OrderDetailLine line : detailsByOrderId.getOrDefault(orderId, List.of())) {
            demand.merge(line.detailId(), line.count(), Integer::sum);
        }

        for (OrderToolLine toolLine : toolsByOrderId.getOrDefault(orderId, List.of())) {
            for (ToolDetailLine spec :
                    toolDetailsByToolId.getOrDefault(toolLine.toolId(), List.of())) {
                int need = toolLine.count() * spec.count();
                demand.merge(spec.detailId(), need, Integer::sum);
            }
            // TODO: учитывать assemble_duration — операция сборки инструмента
        }

        return demand;
    }
}
