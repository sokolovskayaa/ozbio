package scheduler.service;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import scheduler.api.MachineIdleBlockRequest;
import scheduler.api.ShiftCloseRequest;
import scheduler.api.ShiftOperationFactRequest;
import scheduler.engine.ReplanService;
import scheduler.engine.ScheduleMetrics;
import scheduler.model.Assignment;
import scheduler.model.AssignmentStatus;
import scheduler.model.MachineBlock;
import scheduler.model.Order;
import scheduler.model.Part;
import scheduler.model.SetupIntervals;
import scheduler.store.JsonScheduleRepository;
import scheduler.store.ScheduleStore;
import scheduler.time.CurrentTimeProvider;

public class ShiftCloseService {
    private final ScheduleStore store;
    private final JsonScheduleRepository repository;
    private final CurrentTimeProvider time;
    private final ReplanService replanService;
    private final ShiftFactResolver factResolver;

    public ShiftCloseService(
            ScheduleStore store, JsonScheduleRepository repository, CurrentTimeProvider time) {
        this.store = store;
        this.repository = repository;
        this.time = time;
        this.replanService = new ReplanService(time);
        this.factResolver = new ShiftFactResolver(store, time);
    }

    public ShiftCloseResult closeShift(ShiftCloseRequest request) throws IOException {
        if (Boolean.TRUE.equals(request.closeAllPendingShifts())) {
            return closeAllPendingShifts(request);
        }
        return closeSingleGroupShift(request);
    }

    /**
     * Закрытие всех незакрытых смен: сначала факты по всем группам и станкам, затем один переплан.
     */
    private ShiftCloseResult closeAllPendingShifts(ShiftCloseRequest request) throws IOException {
        List<GroupShiftClosePlan> plans = factResolver.resolveAllPending(request);
        Set<String> affected = new LinkedHashSet<>();
        int completedCount = 0;
        int cancelledCount = 0;

        for (GroupShiftClosePlan plan : plans) {
            FactApplyResult applied = applyFacts(plan.operations(), plan.shiftEnd());
            completedCount += applied.completedCount();
            cancelledCount += applied.cancelledCount();
            affected.addAll(applied.affectedOrderIds());
            store.setLastClosedShiftEnd(plan.groupId(), plan.shiftEnd());
        }

        applyIdleBlocks(request.idleBlocks(), affected);

        return finishWithReplan(affected, completedCount, cancelledCount);
    }

    private ShiftCloseResult closeSingleGroupShift(ShiftCloseRequest request) throws IOException {
        ShiftCloseRequest resolved = request;
        if (request.groupId() != null && request.shiftStart() != null && request.shiftEnd() != null) {
            resolved = factResolver.resolve(request);
        }
        Instant shiftEnd = resolved.shiftEnd() != null ? resolved.shiftEnd() : time.now();
        List<ShiftOperationFactRequest> operations =
                resolved.operations() != null ? resolved.operations() : List.of();
        if (operations.isEmpty() && (resolved.groupId() == null || resolved.groupId().isBlank())) {
            throw new SchedulingException("operations list required");
        }

        FactApplyResult applied = applyFacts(operations, shiftEnd);
        Set<String> affected = new LinkedHashSet<>(applied.affectedOrderIds());
        applyIdleBlocks(resolved.idleBlocks(), affected);

        if (resolved.groupId() != null && !resolved.groupId().isBlank()) {
            store.setLastClosedShiftEnd(resolved.groupId(), shiftEnd);
        }

        return finishWithReplan(affected, applied.completedCount(), applied.cancelledCount());
    }

    private ShiftCloseResult finishWithReplan(Set<String> affected, int completedCount, int cancelledCount)
            throws IOException {
        ReplanService.ReplanResult replan = replanService.replan(store, affected);

        Map<String, Instant> readyAt = new LinkedHashMap<>();
        for (String orderId : replan.replannedOrderIds()) {
            readyAt.put(orderId, ScheduleMetrics.readyAt(orderId, store.assignments()));
        }

        repository.save(store);
        return new ShiftCloseResult(
                completedCount,
                cancelledCount,
                replan.cancelledAssignments(),
                replan.replannedOrderIds(),
                Map.copyOf(readyAt));
    }

    private void applyIdleBlocks(List<MachineIdleBlockRequest> idleBlocks, Set<String> affected) {
        if (idleBlocks == null) {
            return;
        }
        for (MachineIdleBlockRequest idle : idleBlocks) {
            store.addMachineBlock(new MachineBlock(
                    idle.machineId(), idle.from(), idle.to(), idle.reason()));
            addOrdersOnMachine(affected, idle.machineId());
        }
    }

    private FactApplyResult applyFacts(List<ShiftOperationFactRequest> operations, Instant shiftEnd) {
        int completedCount = 0;
        int cancelledCount = 0;
        Set<String> affected = new LinkedHashSet<>();
        for (ShiftOperationFactRequest fact : operations) {
            validateFactKeys(fact);
            if (fact.completed()) {
                completedCount += applyCompleted(fact, shiftEnd);
            } else {
                cancelledCount += applyNotCompleted(fact);
            }
            affected.add(fact.orderId());
        }
        return new FactApplyResult(completedCount, cancelledCount, affected);
    }

    private record FactApplyResult(int completedCount, int cancelledCount, Set<String> affectedOrderIds) {}

    private void validateFactKeys(ShiftOperationFactRequest fact) {
        if (fact.orderId() == null || fact.orderId().isBlank()) {
            throw new SchedulingException("orderId required");
        }
        if (!store.hasOrder(fact.orderId())) {
            throw new SchedulingException("Unknown order: " + fact.orderId());
        }
        Order order = store.findOrder(fact.orderId()).orElseThrow();
        Part part = order.parts().stream()
                .filter(p -> p.partId().equals(fact.partId()))
                .findFirst()
                .orElseThrow(() -> new SchedulingException("Unknown part in order: " + fact.partId()));
        if (fact.unitIndex() < 0 || fact.unitIndex() >= part.quantity()) {
            throw new SchedulingException("Invalid unitIndex: " + fact.unitIndex());
        }
        boolean taskFound = part.tasks().stream().anyMatch(t -> t.taskId().equals(fact.taskId()));
        if (!taskFound) {
            throw new SchedulingException("Unknown task: " + fact.taskId());
        }
        if (SetupIntervals.isSetup(fact.taskId())) {
            throw new SchedulingException("Use work taskId, not setup");
        }
    }

    private int applyCompleted(ShiftOperationFactRequest fact, Instant shiftEnd) {
        Assignment work = store.findPlannedWorkAssignment(
                        fact.orderId(), fact.partId(), fact.unitIndex(), fact.taskId())
                .orElseThrow(() -> new SchedulingException(
                        "No planned work for " + fact.orderId() + " " + fact.taskId() + " unit " + fact.unitIndex()));
        Instant actualEnd = fact.actualEnd() != null ? fact.actualEnd() : shiftEnd;
        Instant actualStart = fact.actualStart() != null ? fact.actualStart() : work.plannedStart();
        if (actualEnd.isBefore(actualStart)) {
            throw new SchedulingException("actualEnd before actualStart");
        }
        store.replaceAssignment(toCompleted(work, actualStart, actualEnd));
        store.findPlannedSetupForUnit(fact.orderId(), fact.partId(), fact.unitIndex(), work.machineId())
                .ifPresent(setup -> {
                    Instant setupEnd = actualStart;
                    Instant setupStart = fact.actualStart() != null ? fact.actualStart() : setup.plannedStart();
                    store.replaceAssignment(toCompleted(setup, setupStart, setupEnd));
                });
        return 1;
    }

    private int applyNotCompleted(ShiftOperationFactRequest fact) {
        int count = 0;
        Assignment work = store.findPlannedWorkAssignment(
                        fact.orderId(), fact.partId(), fact.unitIndex(), fact.taskId())
                .orElse(null);
        if (work != null) {
            store.replaceAssignment(toCancelled(work));
            count++;
            var setup = store.findPlannedSetupForUnit(
                    fact.orderId(), fact.partId(), fact.unitIndex(), work.machineId());
            if (setup.isPresent()) {
                store.replaceAssignment(toCancelled(setup.get()));
                count++;
            }
        }
        return count;
    }

    private static Assignment toCompleted(Assignment a, Instant actualStart, Instant actualEnd) {
        return new Assignment(
                a.assignmentId(),
                a.orderId(),
                a.partId(),
                a.unitIndex(),
                a.taskId(),
                a.sequence(),
                a.machineId(),
                a.plannedStart(),
                a.plannedEnd(),
                AssignmentStatus.COMPLETED,
                actualStart,
                actualEnd);
    }

    private static Assignment toCancelled(Assignment a) {
        return new Assignment(
                a.assignmentId(),
                a.orderId(),
                a.partId(),
                a.unitIndex(),
                a.taskId(),
                a.sequence(),
                a.machineId(),
                a.plannedStart(),
                a.plannedEnd(),
                AssignmentStatus.CANCELLED,
                a.actualStart(),
                a.actualEnd());
    }

    private void addOrdersOnMachine(Set<String> affected, String machineId) {
        for (Assignment a : store.assignments()) {
            if (a.machineId().equals(machineId) && !a.isCancelled()) {
                affected.add(a.orderId());
            }
        }
    }
}
