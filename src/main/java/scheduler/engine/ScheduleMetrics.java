package scheduler.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import scheduler.model.Assignment;
import scheduler.model.AssignmentStatus;
import scheduler.model.Capability;
import scheduler.model.Order;
import scheduler.model.Part;
import scheduler.model.Task;
import scheduler.store.ScheduleStore;
import scheduler.time.CurrentTimeProvider;

public final class ScheduleMetrics {
    private ScheduleMetrics() {}

    public static Instant readyAt(String orderId, List<Assignment> assignments) {
        return activeAssignments(assignments).stream()
                .filter(a -> a.orderId().equals(orderId))
                .map(Assignment::effectiveEnd)
                .max(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException("No assignments for order " + orderId));
    }

    public static Instant partReadyAt(String orderId, String partId, List<Assignment> assignments) {
        return activeAssignments(assignments).stream()
                .filter(a -> a.orderId().equals(orderId) && a.partId().equals(partId))
                .map(Assignment::effectiveEnd)
                .max(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException("No assignments for part " + partId));
    }

    public static Instant orderStart(Order order, ScheduleStore store, CurrentTimeProvider time) {
        Instant base = order.createdAt().isBefore(store.factoryStartedAt())
                ? store.factoryStartedAt()
                : order.createdAt();
        Instant now = time.now();
        return base.isBefore(now) ? now : base;
    }

    public static boolean isPartFullyScheduled(
            String orderId, Part part, List<Assignment> assignments) {
        return nextUnscheduledUnitIndex(orderId, part, assignments) >= part.quantity();
    }

    /** Сколько штук имеют завершённую или запланированную рабочую операцию {@code taskId}. */
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

    /** Индекс следующей штуки без полного плана по всем операциям (0..quantity-1). */
    public static int nextUnscheduledUnitIndex(String orderId, Part part, List<Assignment> assignments) {
        for (int unit = 0; unit < part.quantity(); unit++) {
            if (!isUnitFullyScheduled(orderId, part, unit, assignments)) {
                return unit;
            }
        }
        return part.quantity();
    }

    /** Все операции штуки есть в плане (PLANNED или COMPLETED). */
    public static boolean isUnitFullyScheduled(
            String orderId, Part part, int unitIndex, List<Assignment> assignments) {
        for (Task task : part.tasks()) {
            if (!isWorkTaskScheduled(orderId, part, unitIndex, task.taskId(), assignments)) {
                return false;
            }
        }
        return true;
    }

    /** Все операции штуки выполнены по факту. */
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
            Order order, Part part, List<Assignment> assignments, Instant orderStart, ScheduleStore store) {
        for (int unit = 0; unit < part.quantity(); unit++) {
            for (Task task : part.tasks()) {
                if (isWorkTaskDone(order.orderId(), part, unit, task.taskId(), assignments)) {
                    continue;
                }
                if (hasPlannedWork(order.orderId(), part.partId(), unit, task.taskId(), assignments)) {
                    continue;
                }
                if (isTaskReady(order, part, unit, task, assignments, orderStart, store)) {
                    return Optional.of(new ReadyWork(unit, task));
                }
                break;
            }
        }
        return Optional.empty();
    }

    public static Optional<Task> readyTask(
            Order order, Part part, List<Assignment> assignments, Instant orderStart, ScheduleStore store) {
        return readyWork(order, part, assignments, orderStart, store).map(ReadyWork::task);
    }

    public static boolean isTaskReady(
            Order order,
            Part part,
            int unitIndex,
            Task task,
            List<Assignment> assignments,
            Instant orderStart,
            ScheduleStore store) {
        if (!isEarlierSameMachineBatchComplete(order, part, task, assignments, store)) {
            return false;
        }
        if (task.sequence() > 0) {
            Task previousInRoute = part.tasks().stream()
                    .filter(t -> t.sequence() == task.sequence() - 1)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Missing route task sequence " + (task.sequence() - 1)));
            if (!store.overlapBatchesEnabled()) {
                if (unitIndex == 0
                        && !isBatchSequenceComplete(order, part, task.sequence() - 1, assignments)) {
                    return false;
                }
            } else {
                if (!isWorkTaskScheduled(
                        order.orderId(), part, unitIndex, previousInRoute.taskId(), assignments)) {
                    return false;
                }
                BatchOverlap.OverlapMode mode = BatchOverlap.overlapMode(store, previousInRoute, task);
                if (unitIndex == 0
                        && mode == BatchOverlap.OverlapMode.PIPELINE
                        && !isBatchSequenceComplete(order, part, task.sequence() - 1, assignments)
                        && requiresFullPreviousBatchBeforePackageStart(
                                store, previousInRoute, task)) {
                    return false;
                }
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
            ScheduleStore store,
            String targetMachineId) {
        Instant routeConstraint = previousTaskEndFromRoute(
                order, part, unitIndex, sequence, assignments, orderStart, store);
        Instant sameMachineConstraint = latestEarlierSharedMachineBatchEnd(
                order, part, sequence, assignments, store, targetMachineId);
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
            Instant orderStart,
            ScheduleStore store) {
        if (sequence > 0) {
            Task previousInRoute = part.tasks().stream()
                    .filter(t -> t.sequence() == sequence - 1)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Missing route task sequence " + (sequence - 1)));
            Task currentInRoute = part.tasks().stream()
                    .filter(t -> t.sequence() == sequence)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Missing route task sequence " + sequence));
            Instant prevUnitEnd = endOfUnitOperation(
                    order.orderId(), part.partId(), unitIndex, previousInRoute.sequence(), assignments);
            if (unitIndex > 0) {
                Instant sameOpPrev = endOfUnitOperation(
                        order.orderId(), part.partId(), unitIndex - 1, sequence, assignments);
                return prevUnitEnd.isAfter(sameOpPrev) ? prevUnitEnd : sameOpPrev;
            }
            if (!store.overlapBatchesEnabled()) {
                return batchMaxEffectiveEnd(order, part, previousInRoute, assignments);
            }
            return switch (BatchOverlap.overlapMode(store, previousInRoute, currentInRoute)) {
                case NONE -> batchMaxEffectiveEnd(order, part, previousInRoute, assignments);
                case PER_UNIT -> prevUnitEnd;
                case PIPELINE -> BatchOverlap.earliestPackageStartForContinuousFeed(
                        order, part, sequence, assignments, orderStart);
            };
        }
        if (unitIndex == 0) {
            return orderStart;
        }
        return endOfUnitOperation(order.orderId(), part.partId(), unitIndex - 1, 0, assignments);
    }

    /**
     * На одном физическом станке нельзя начинать op.N+1, пока на <strong>этом же</strong> станке
     * не завершён пакет op.N. При overlap между op на разных станках (≥2 общих станка) полный
     * пакет op.N по всему цеху не требуется.
     */
    private static boolean isEarlierSameMachineBatchComplete(
            Order order, Part part, Task task, List<Assignment> assignments, ScheduleStore store) {
        for (Task earlier : part.tasks()) {
            if (earlier.sequence() >= task.sequence()) {
                continue;
            }
            if (!BatchOverlap.sharesOperationalMachine(
                    store, earlier.requiredCapability(), task.requiredCapability())) {
                continue;
            }
            if (BatchOverlap.overlapMode(store, earlier, task) != BatchOverlap.OverlapMode.NONE) {
                continue;
            }
            if (!isBatchSequenceComplete(order, part, earlier.sequence(), assignments)) {
                return false;
            }
        }
        return true;
    }

    private static Instant latestEarlierSharedMachineBatchEnd(
            Order order,
            Part part,
            int currentSequence,
            List<Assignment> assignments,
            ScheduleStore store,
            String targetMachineId) {
        Task current = taskAtSequence(part, currentSequence);
        Instant latest = null;
        for (Task earlier : part.tasks()) {
            if (earlier.sequence() >= currentSequence) {
                continue;
            }
            if (!BatchOverlap.sharesOperationalMachine(
                    store, earlier.requiredCapability(), current.requiredCapability())) {
                continue;
            }
            if (BatchOverlap.overlapMode(store, earlier, current) != BatchOverlap.OverlapMode.NONE) {
                Optional<Instant> onTarget = batchMaxEffectiveEndOnMachine(
                        order, part, earlier, assignments, targetMachineId);
                if (onTarget.isPresent()
                        && (latest == null || onTarget.get().isAfter(latest))) {
                    latest = onTarget.get();
                }
                continue;
            }
            Instant batchEnd = batchMaxEffectiveEnd(order, part, earlier, assignments);
            if (latest == null || batchEnd.isAfter(latest)) {
                latest = batchEnd;
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

    private static Task taskAtSequence(Part part, int sequence) {
        return part.tasks().stream()
                .filter(t -> t.sequence() == sequence)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing task sequence " + sequence));
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

    private static List<Assignment> activeAssignments(List<Assignment> assignments) {
        return AssignmentFilters.active(assignments);
    }

    /** Весь пакет op.N в плане перед штукой 0 op.N+1 (две токарки; фреза→токарка). Только при overlap. */
    private static boolean requiresFullPreviousBatchBeforePackageStart(
            ScheduleStore store, Task previous, Task current) {
        if (!store.overlapBatchesEnabled()) {
            return false;
        }
        int shared = BatchOverlap.sharedOperationalMachines(
                        store, previous.requiredCapability(), current.requiredCapability())
                .size();
        if (shared >= 2) {
            return true;
        }
        return shared == 0
                && previous.requiredCapability() == Capability.MILLING
                && current.requiredCapability() == Capability.TURNING;
    }
}
