package scheduler.engine.metrics;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import scheduler.model.schedule.Assignment;
import scheduler.model.schedule.AssignmentStatus;
import scheduler.model.order.Order;
import scheduler.model.order.Part;
import scheduler.model.order.Task;
import scheduler.engine.planning.ReadyWork;

/** Готовность операций и прогресс планирования по штукам. */
public final class TaskReadiness {
    private TaskReadiness() {}

    public static boolean isPartFullyScheduled(String orderId, Part part, List<Assignment> assignments) {
        return nextUnscheduledUnitIndex(orderId, part, assignments) >= part.quantity();
    }

    public static int unitsScheduledForTask(
            String orderId, String partId, String taskId, List<Assignment> assignments) {
        return (int) AssignmentFilters.work(assignments).stream()
                .filter(a -> a.orderId().equals(orderId)
                        && a.partId().equals(partId)
                        && a.taskId().equals(taskId))
                .filter(a -> a.isCompleted() || a.isPlanned())
                .map(Assignment::unitIndex)
                .distinct()
                .count();
    }

    public static Duration taskSpanOnTimeline(
            String orderId, String partId, String taskId, List<Assignment> assignments) {
        List<Assignment> taskAssignments = AssignmentFilters.work(assignments).stream()
                .filter(a -> a.orderId().equals(orderId)
                        && a.partId().equals(partId)
                        && a.taskId().equals(taskId))
                .filter(a -> a.status() != AssignmentStatus.CANCELLED)
                .toList();
        if (taskAssignments.isEmpty()) {
            return Duration.ZERO;
        }
        Instant start = taskAssignments.stream()
                .map(Assignment::effectiveStart)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        Instant end = taskAssignments.stream()
                .map(Assignment::effectiveEnd)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        return Duration.between(start, end);
    }

    public static int nextUnscheduledUnitIndex(String orderId, Part part, List<Assignment> assignments) {
        for (int unit = 0; unit < part.quantity(); unit++) {
            if (!isUnitFullyScheduled(orderId, part, unit, assignments)) {
                return unit;
            }
        }
        return part.quantity();
    }

    public static boolean isUnitFullyScheduled(
            String orderId, Part part, int unitIndex, List<Assignment> assignments) {
        for (Task task : part.tasks()) {
            if (!isWorkTaskScheduled(orderId, part, unitIndex, task.taskId(), assignments)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isUnitComplete(
            String orderId, Part part, int unitIndex, List<Assignment> assignments) {
        for (Task task : part.tasks()) {
            if (!isWorkTaskDone(orderId, part, unitIndex, task.taskId(), assignments)) {
                return false;
            }
        }
        return true;
    }

    public static Optional<ReadyWork> readyWork(
            Order order, Part part, List<Assignment> assignments, Instant orderStart) {
        for (int unit = 0; unit < part.quantity(); unit++) {
            for (Task task : part.tasks()) {
                if (isWorkTaskDone(order.orderId(), part, unit, task.taskId(), assignments)) {
                    continue;
                }
                if (hasPlannedWork(order.orderId(), part.partId(), unit, task.taskId(), assignments)) {
                    continue;
                }
                if (isTaskReady(order, part, unit, task, assignments, orderStart)) {
                    return Optional.of(new ReadyWork(unit, task));
                }
                break;
            }
        }
        return Optional.empty();
    }

    public static boolean isTaskReady(
            Order order,
            Part part,
            int unitIndex,
            Task task,
            List<Assignment> assignments,
            Instant orderStart) {
        if (!isEarlierSameMachineBatchComplete(order, part, task, assignments)) {
            return false;
        }
        if (task.sequence() > 0) {
            if (unitIndex == 0
                    && !isBatchSequenceComplete(order, part, task.sequence() - 1, assignments)) {
                return false;
            }
        }
        if (unitIndex == 0) {
            return true;
        }
        return isWorkTaskScheduled(order.orderId(), part, unitIndex - 1, task.taskId(), assignments);
    }

    public static Instant previousTaskEnd(
            Order order,
            Part part,
            int unitIndex,
            int sequence,
            List<Assignment> assignments,
            Instant orderStart,
            String targetMachineId) {
        Instant routeConstraint = previousTaskEndFromRoute(
                order, part, unitIndex, sequence, assignments, orderStart);
        Instant sameMachineConstraint = latestEarlierBatchEndOnMachine(
                order, part, sequence, assignments, targetMachineId);
        if (sameMachineConstraint == null) {
            return routeConstraint;
        }
        return routeConstraint.isAfter(sameMachineConstraint) ? routeConstraint : sameMachineConstraint;
    }

    private static Instant previousTaskEndFromRoute(
            Order order,
            Part part,
            int unitIndex,
            int sequence,
            List<Assignment> assignments,
            Instant orderStart) {
        if (sequence > 0) {
            Task previousInRoute = part.tasks().stream()
                    .filter(t -> t.sequence() == sequence - 1)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Missing route task sequence " + (sequence - 1)));
            Instant prevUnitEnd = endOfUnitOperation(
                    order.orderId(), part.partId(), unitIndex, previousInRoute.sequence(), assignments);
            if (unitIndex > 0) {
                Instant sameOpPrev = endOfUnitOperation(
                        order.orderId(), part.partId(), unitIndex - 1, sequence, assignments);
                return prevUnitEnd.isAfter(sameOpPrev) ? prevUnitEnd : sameOpPrev;
            }
            return batchMaxEffectiveEnd(order, part, previousInRoute, assignments);
        }
        if (unitIndex == 0) {
            return orderStart;
        }
        return endOfUnitOperation(order.orderId(), part.partId(), unitIndex - 1, 0, assignments);
    }

    private static boolean isEarlierSameMachineBatchComplete(
            Order order, Part part, Task task, List<Assignment> assignments) {
        for (Task earlier : part.tasks()) {
            if (earlier.sequence() >= task.sequence()) {
                continue;
            }
            boolean usedOnAnyMachine = AssignmentFilters.work(assignments).stream()
                    .anyMatch(a -> a.orderId().equals(order.orderId())
                            && a.partId().equals(part.partId())
                            && a.taskId().equals(earlier.taskId())
                            && (a.isCompleted() || a.isPlanned()));
            if (!usedOnAnyMachine) {
                continue;
            }
            if (!isBatchSequenceComplete(order, part, earlier.sequence(), assignments)) {
                return false;
            }
        }
        return true;
    }

    private static Instant latestEarlierBatchEndOnMachine(
            Order order,
            Part part,
            int currentSequence,
            List<Assignment> assignments,
            String targetMachineId) {
        Instant latest = null;
        for (Task earlier : part.tasks()) {
            if (earlier.sequence() >= currentSequence) {
                continue;
            }
            Optional<Instant> onTarget = batchMaxEffectiveEndOnMachine(
                    order, part, earlier, assignments, targetMachineId);
            if (onTarget.isPresent() && (latest == null || onTarget.get().isAfter(latest))) {
                latest = onTarget.get();
            }
        }
        return latest;
    }

    private static Optional<Instant> batchMaxEffectiveEndOnMachine(
            Order order,
            Part part,
            Task task,
            List<Assignment> assignments,
            String machineId) {
        return AssignmentFilters.work(assignments).stream()
                .filter(a -> a.orderId().equals(order.orderId()))
                .filter(a -> a.partId().equals(part.partId()))
                .filter(a -> a.taskId().equals(task.taskId()))
                .filter(a -> a.machineId().equals(machineId))
                .filter(a -> a.isCompleted() || a.isPlanned())
                .map(a -> a.isCompleted() ? a.effectiveEnd() : a.plannedEnd())
                .max(Comparator.naturalOrder());
    }

    private static Instant batchMaxEffectiveEnd(
            Order order, Part part, Task task, List<Assignment> assignments) {
        return AssignmentFilters.work(assignments).stream()
                .filter(a -> a.orderId().equals(order.orderId()))
                .filter(a -> a.partId().equals(part.partId()))
                .filter(a -> a.taskId().equals(task.taskId()))
                .filter(a -> a.isCompleted() || a.isPlanned())
                .map(a -> a.isCompleted() ? a.effectiveEnd() : a.plannedEnd())
                .max(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException("Batch operation " + task.taskId() + " not complete"));
    }

    private static Instant endOfUnitOperation(
            String orderId, String partId, int unitIndex, int sequence, List<Assignment> assignments) {
        return AssignmentFilters.work(assignments).stream()
                .filter(a -> a.orderId().equals(orderId)
                        && a.partId().equals(partId)
                        && a.unitIndex() == unitIndex
                        && a.sequence() == sequence)
                .filter(a -> a.isCompleted() || a.isPlanned())
                .map(a -> a.isCompleted() ? a.effectiveEnd() : a.plannedEnd())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Missing predecessor unit for part " + partId));
    }

    private static boolean isBatchSequenceComplete(
            Order order, Part part, int sequence, List<Assignment> assignments) {
        Optional<Task> taskAtSequence = part.tasks().stream()
                .filter(t -> t.sequence() == sequence)
                .findFirst();
        if (taskAtSequence.isEmpty()) {
            return true;
        }
        String taskId = taskAtSequence.get().taskId();
        for (int unit = 0; unit < part.quantity(); unit++) {
            if (!isWorkTaskScheduled(order.orderId(), part, unit, taskId, assignments)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isWorkTaskDone(
            String orderId, Part part, int unitIndex, String taskId, List<Assignment> assignments) {
        return AssignmentFilters.work(assignments).stream()
                .anyMatch(a -> a.orderId().equals(orderId)
                        && a.partId().equals(part.partId())
                        && a.unitIndex() == unitIndex
                        && a.taskId().equals(taskId)
                        && a.isCompleted());
    }

    public static boolean isWorkTaskScheduled(
            String orderId, Part part, int unitIndex, String taskId, List<Assignment> assignments) {
        return AssignmentFilters.work(assignments).stream()
                .anyMatch(a -> a.orderId().equals(orderId)
                        && a.partId().equals(part.partId())
                        && a.unitIndex() == unitIndex
                        && a.taskId().equals(taskId)
                        && (a.isCompleted() || a.isPlanned()));
    }

    private static boolean hasPlannedWork(
            String orderId, String partId, int unitIndex, String taskId, List<Assignment> assignments) {
        return AssignmentFilters.work(assignments).stream()
                .anyMatch(a -> a.orderId().equals(orderId)
                        && a.partId().equals(partId)
                        && a.unitIndex() == unitIndex
                        && a.taskId().equals(taskId)
                        && a.isPlanned());
    }
}
