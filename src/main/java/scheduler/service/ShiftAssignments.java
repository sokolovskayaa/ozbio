package scheduler.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import scheduler.engine.AssignmentFilters;
import scheduler.model.Assignment;
import scheduler.model.Machine;
import scheduler.model.Order;
import scheduler.model.SetupIntervals;
import scheduler.store.ScheduleStore;

/** Плановые операции в окне смены и агрегаты по станку/типу. */
final class ShiftAssignments {
    private ShiftAssignments() {}

    static boolean overlapsShift(Assignment a, Instant shiftStart, Instant shiftEnd) {
        return !a.plannedEnd().isBefore(shiftStart) && !a.plannedStart().isAfter(shiftEnd);
    }

    static List<Assignment> plannedWorkInShift(
            ScheduleStore store, String groupId, Instant shiftStart, Instant shiftEnd) {
        List<Assignment> list = new ArrayList<>();
        for (Assignment a : AssignmentFilters.work(store.assignments())) {
            if (!a.isPlanned()) {
                continue;
            }
            if (SetupIntervals.isSetup(a.taskId())) {
                continue;
            }
            Machine machine = store.findMachine(a.machineId());
            if (!machine.groupId().equals(groupId)) {
                continue;
            }
            if (!overlapsShift(a, shiftStart, shiftEnd)) {
                continue;
            }
            list.add(a);
        }
        list.sort(assignmentOrder(store));
        return List.copyOf(list);
    }

    static Map<AggregateKey, List<Assignment>> aggregateByMachineAndTask(
            ScheduleStore store, String groupId, Instant shiftStart, Instant shiftEnd) {
        Map<AggregateKey, List<Assignment>> map = new LinkedHashMap<>();
        for (Assignment a : plannedWorkInShift(store, groupId, shiftStart, shiftEnd)) {
            AggregateKey key = new AggregateKey(a.machineId(), a.taskId());
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(a);
        }
        return Map.copyOf(map);
    }

    static Comparator<Assignment> assignmentOrder(ScheduleStore store) {
        return Comparator.comparing(Assignment::plannedStart)
                .thenComparing(
                        (Assignment a) -> store.findOrder(a.orderId())
                                .map(Order::priority)
                                .orElse(0),
                        Comparator.reverseOrder())
                .thenComparing(Assignment::orderId)
                .thenComparingInt(Assignment::unitIndex);
    }

    static boolean machineInGroup(ScheduleStore store, String machineId, String groupId) {
        return store.findMachine(machineId).groupId().equals(groupId);
    }

    record AggregateKey(String machineId, String taskId) {}

    static String aggregateMapKey(String machineId, String taskId) {
        return machineId + "\0" + taskId;
    }

    static AggregateKey parseKey(String key) {
        int sep = key.indexOf('\0');
        return new AggregateKey(key.substring(0, sep), key.substring(sep + 1));
    }

    static String toMapKey(AggregateKey key) {
        return aggregateMapKey(key.machineId(), key.taskId());
    }

    static boolean isInsideOngoingShift(
            ScheduleStore store, String groupId, Instant now, scheduler.engine.ShiftCalendar.ShiftWindow window) {
        return !now.isBefore(window.start()) && now.isBefore(window.end());
    }
}
