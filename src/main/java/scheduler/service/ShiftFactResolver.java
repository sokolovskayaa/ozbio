package scheduler.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import scheduler.api.MachineTaskCountRequest;
import scheduler.api.ShiftCloseRequest;
import scheduler.api.ShiftOperationFactRequest;
import scheduler.model.Assignment;
import scheduler.store.ScheduleStore;
import scheduler.time.CurrentTimeProvider;
import scheduler.time.StoreCurrentTimeProvider;

public class ShiftFactResolver {
    private final ScheduleStore store;
    private final CurrentTimeProvider time;

    public ShiftFactResolver(ScheduleStore store) {
        this(store, new StoreCurrentTimeProvider(store));
    }

    ShiftFactResolver(ScheduleStore store, CurrentTimeProvider time) {
        this.store = store;
        this.time = time;
    }

    /**
     * Все незакрытые смены: факты по каждой группе. Требует явный {@code completedCount} для каждой
     * строки (группа + станок + операция) из {@link ShiftContextView#closeRows()}.
     */
    public List<GroupShiftClosePlan> resolveAllPending(ShiftCloseRequest request) {
        Instant shiftEnd = request.shiftEnd() != null ? request.shiftEnd() : time.now();
        Map<String, Integer> counts = indexCounts(request.machineTaskCounts(), true);
        List<ShiftPendingShifts.Entry> pending = ShiftPendingShifts.list(store, shiftEnd);
        if (pending.isEmpty()) {
            throw new SchedulingException("No pending shifts to close");
        }
        List<GroupShiftClosePlan> plans = new ArrayList<>();
        for (ShiftPendingShifts.Entry entry : pending) {
            Map<ShiftAssignments.AggregateKey, List<Assignment>> planned =
                    ShiftAssignments.aggregateByMachineAndTask(
                            store, entry.groupId(), entry.window().start(), entry.window().end());
            List<ShiftOperationFactRequest> operations = new ArrayList<>();
            for (var aggregate : planned.entrySet()) {
                ShiftAssignments.AggregateKey key = aggregate.getKey();
                String rowKey = closeRowKey(entry.groupId(), key.machineId(), key.taskId());
                if (!counts.containsKey(rowKey)) {
                    throw new SchedulingException(
                            "Missing completedCount for "
                                    + entry.groupId()
                                    + " / "
                                    + key.machineId()
                                    + " / "
                                    + key.taskId()
                                    + " (fill all stations before closing)");
                }
                int completedCount = counts.get(rowKey);
                operations.addAll(toFacts(
                        aggregate.getValue(), completedCount, entry.window().end()));
            }
            plans.add(new GroupShiftClosePlan(
                    entry.groupId(), entry.window().start(), entry.window().end(), List.copyOf(operations)));
        }
        return List.copyOf(plans);
    }

    public ShiftCloseRequest resolve(ShiftCloseRequest request) {
        validateAggregateRequest(request);

        String groupId = request.groupId();
        Instant shiftStart = request.shiftStart();
        Instant shiftEnd = request.shiftEnd() != null ? request.shiftEnd() : shiftStart;

        if (request.machineTaskCounts() != null) {
            for (MachineTaskCountRequest row : request.machineTaskCounts()) {
                validateCountRow(row, groupId);
            }
        }
        Map<String, Integer> counts = indexCounts(request.machineTaskCounts(), false);

        Map<ShiftAssignments.AggregateKey, List<Assignment>> planned =
                ShiftAssignments.aggregateByMachineAndTask(store, groupId, shiftStart, shiftEnd);

        List<ShiftOperationFactRequest> operations = new ArrayList<>();
        for (var entry : planned.entrySet()) {
            ShiftAssignments.AggregateKey key = entry.getKey();
            List<Assignment> assignments = entry.getValue();
            int plannedCount = assignments.size();
            int completedCount =
                    counts.getOrDefault(ShiftAssignments.toMapKey(key), plannedCount);
            operations.addAll(toFacts(assignments, completedCount, shiftEnd));
        }

        return new ShiftCloseRequest(
                shiftEnd,
                groupId,
                shiftStart,
                operations,
                null,
                request.idleBlocks(),
                false);
    }

    private static String closeRowKey(String groupId, String machineId, String taskId) {
        return groupId + "\0" + machineId + "\0" + taskId;
    }

    private Map<String, Integer> indexCounts(List<MachineTaskCountRequest> rows, boolean requireGroupId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (rows == null) {
            return counts;
        }
        for (MachineTaskCountRequest row : rows) {
            if (requireGroupId && (row.groupId() == null || row.groupId().isBlank())) {
                throw new SchedulingException("groupId required for each machineTaskCounts row");
            }
            String key = requireGroupId
                    ? closeRowKey(row.groupId(), row.machineId(), row.taskId())
                    : ShiftAssignments.toMapKey(new ShiftAssignments.AggregateKey(row.machineId(), row.taskId()));
            counts.put(key, row.completedCount());
        }
        return counts;
    }

    private static List<ShiftOperationFactRequest> toFacts(
            List<Assignment> assignments, int completedCount, Instant shiftEnd) {
        int plannedCount = assignments.size();
        if (completedCount < 0 || completedCount > plannedCount) {
            ShiftAssignments.AggregateKey sample = new ShiftAssignments.AggregateKey(
                    assignments.getFirst().machineId(), assignments.getFirst().taskId());
            throw new SchedulingException(
                    "completedCount must be 0.."
                            + plannedCount
                            + " for "
                            + sample.machineId()
                            + " / "
                            + sample.taskId());
        }
        List<ShiftOperationFactRequest> operations = new ArrayList<>();
        for (int i = 0; i < assignments.size(); i++) {
            Assignment a = assignments.get(i);
            boolean completed = i < completedCount;
            Instant actualEnd = completed ? effectiveActualEnd(a, shiftEnd) : null;
            operations.add(new ShiftOperationFactRequest(
                    a.orderId(), a.partId(), a.unitIndex(), a.taskId(), completed, null, actualEnd));
        }
        return operations;
    }

    private static Instant effectiveActualEnd(Assignment work, Instant shiftEnd) {
        return work.plannedEnd().isBefore(shiftEnd) ? work.plannedEnd() : shiftEnd;
    }

    private void validateAggregateRequest(ShiftCloseRequest request) {
        if (request.groupId() == null || request.groupId().isBlank()) {
            throw new SchedulingException("groupId required for aggregate shift close");
        }
        if (request.shiftStart() == null) {
            throw new SchedulingException("shiftStart required");
        }
        if (request.shiftEnd() == null) {
            throw new SchedulingException("shiftEnd required");
        }
        if (!request.shiftEnd().isAfter(request.shiftStart())
                && !request.shiftEnd().equals(request.shiftStart())) {
            throw new SchedulingException("shiftEnd must be after shiftStart");
        }
        store.findMachineGroup(request.groupId());
    }

    private void validateCountRow(MachineTaskCountRequest row, String groupId) {
        if (row.machineId() == null || row.machineId().isBlank()) {
            throw new SchedulingException("machineId required");
        }
        if (row.taskId() == null || row.taskId().isBlank()) {
            throw new SchedulingException("taskId required");
        }
        if (!ShiftAssignments.machineInGroup(store, row.machineId(), groupId)) {
            throw new SchedulingException("Machine " + row.machineId() + " not in group " + groupId);
        }
    }
}
