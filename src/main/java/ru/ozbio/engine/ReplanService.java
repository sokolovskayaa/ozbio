package ru.ozbio.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import ru.ozbio.engine.model.PlanningBatch;
import ru.ozbio.persistence.DetailRepository;
import ru.ozbio.persistence.OrderRepository;
import ru.ozbio.persistence.ToolRepository;
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

    public ReplanService(
            OrderRepository orderRepository,
            ToolRepository toolRepository,
            DetailRepository detailRepository) {
        this.orderRepository = orderRepository;
        this.toolRepository = toolRepository;
        this.detailRepository = detailRepository;
    }

    public void replan() {
        // TODO: implement
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
            Map<Long, Integer> demand = demandPerOrder.get(i);

            // TODO: приоритет деталей внутри заказа при формировании батчей
            for (var entry : demand.entrySet()) {
                long detailId = entry.getKey();
                int quantity = entry.getValue();

                for (OperationLine operation :
                        operationsByDetailId.getOrDefault(detailId, List.of())) {
                    // TODO: учитывать остатки на storage при расчёте count
                    batches.add(new PlanningBatch(operation.id(), quantity));
                }
            }
        }

        return batches;
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
