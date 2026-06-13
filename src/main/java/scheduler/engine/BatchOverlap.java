package scheduler.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import scheduler.model.Assignment;
import scheduler.model.Capability;
import scheduler.model.Machine;
import scheduler.model.Order;
import scheduler.model.Part;
import scheduler.model.Task;
import scheduler.store.ScheduleStore;

/**
 * Параллельные пакеты между операциями маршрута на разных станках: непрерывный пакет op.N+1,
 * для каждой штуки k — op.N на k завершена до старта op.N+1 на k.
 */
public final class BatchOverlap {
    private BatchOverlap() {}

    public enum OverlapMode {
        /** Одна машина на обе операции — классический пакет. */
        NONE,
        /** ≥2 станка или разные capability с конвейером (фреза→расточка, две токарки). */
        PIPELINE,
        /** Разные capability без пакетного конвейера (чистовая токарка → шлифование). */
        PER_UNIT
    }

    /**
     * Минимальный момент старта <strong>рабочей</strong> операции {@code sequence} для штуки 0 пакета:
     * {@code T_anchor + Q·p_prev − (Q−1)·p_cur} при {@code p_prev ≥ p_cur}, иначе {@code T_anchor + p_prev}.
     */
    public static Instant earliestBatchWorkStart(
            Order order,
            Part part,
            int sequence,
            List<Assignment> assignments,
            Instant orderStart) {
        if (sequence <= 0) {
            return orderStart;
        }
        Task previous = taskAtSequence(part, sequence - 1);
        Task current = taskAtSequence(part, sequence);
        int quantity = part.quantity();
        Duration pPrev = previous.duration();
        Duration pCur = current.duration();
        Instant anchor = batchAnchorStart(order, part, sequence - 1, assignments, orderStart);
        long prevMin = pPrev.toMinutes();
        long curMin = pCur.toMinutes();
        long offsetMin;
        if (prevMin >= curMin) {
            offsetMin = quantity * prevMin - (long) (quantity - 1) * curMin;
        } else {
            offsetMin = prevMin;
        }
        return anchor.plus(Duration.ofMinutes(Math.max(0, offsetMin)));
    }

    /**
     * Минимальный старт пакета op.N+1 (штука 0), если весь предыдущий пакет уже в плане:
     * {@code max_k(end(prev,k) − k·p_cur)}. Иначе — конец op.N для штуки 0.
     */
    public static Instant earliestPackageStartForContinuousFeed(
            Order order,
            Part part,
            int sequence,
            List<Assignment> assignments,
            Instant orderStart) {
        if (sequence <= 0) {
            return orderStart;
        }
        Task previous = taskAtSequence(part, sequence - 1);
        Task current = taskAtSequence(part, sequence);
        if (!isUnitScheduled(order, part, 0, previous.taskId(), assignments)) {
            return orderStart;
        }
        Instant unit0End = endOfUnit(order, part, 0, previous.sequence(), assignments);
        if (!isEntirePreviousBatchScheduled(order, part, previous, assignments)) {
            return unit0End;
        }
        Duration pCur = current.duration();
        int quantity = part.quantity();
        Instant t0 = orderStart;
        for (int k = 0; k < quantity; k++) {
            Instant endPrevK = endOfUnit(order, part, k, previous.sequence(), assignments);
            Instant required = endPrevK.minus(Duration.ofMinutes((long) k * pCur.toMinutes()));
            if (required.isAfter(t0)) {
                t0 = required;
            }
        }
        if (t0.isBefore(unit0End)) {
            t0 = unit0End;
        }
        return t0;
    }

    /** Старт пакета предыдущей операции (первая штука) или {@code orderStart}. */
    public static Instant batchAnchorStart(
            Order order,
            Part part,
            int sequence,
            List<Assignment> assignments,
            Instant orderStart) {
        if (sequence <= 0) {
            return orderStart;
        }
        Optional<Instant> firstStart = AssignmentFilters.work(assignments).stream()
                .filter(a -> a.orderId().equals(order.orderId()))
                .filter(a -> a.partId().equals(part.partId()))
                .filter(a -> a.sequence() == sequence)
                .map(Assignment::plannedStart)
                .min(Comparator.naturalOrder());
        return firstStart.orElse(orderStart);
    }

    public static OverlapMode overlapMode(ScheduleStore store, Task previous, Task current) {
        if (!store.overlapBatchesEnabled()) {
            return OverlapMode.NONE;
        }
        int shared = sharedOperationalMachines(store, previous.requiredCapability(), current.requiredCapability())
                .size();
        if (shared == 1) {
            return OverlapMode.NONE;
        }
        if (shared >= 2) {
            return OverlapMode.PIPELINE;
        }
        return usesPipelineBatchStart(previous, current) ? OverlapMode.PIPELINE : OverlapMode.PER_UNIT;
    }

    /**
     * Overlap между операциями маршрута, если их можно развести по разным станкам.
     */
    public static boolean allowsParallelBatchesBetween(ScheduleStore store, Task previous, Task current) {
        if (!store.overlapBatchesEnabled()) {
            return false;
        }
        return overlapMode(store, previous, current) != OverlapMode.NONE;
    }

    /** Пакетный конвейер (не режим «только по штуке», как токарка → шлифование). */
    public static boolean usesPipelineBatchStart(Task previous, Task current) {
        return !(previous.requiredCapability() == Capability.TURNING
                && current.requiredCapability() == Capability.GRINDING);
    }

    /** Станки, на которых могут выполняться обе операции. */
    public static List<Machine> sharedOperationalMachines(ScheduleStore store, Capability a, Capability b) {
        List<Machine> result = new ArrayList<>();
        for (Machine machine : operationalMachines(store, a)) {
            if (machine.canPerform(b)) {
                result.add(machine);
            }
        }
        return List.copyOf(result);
    }

    /** Обе операции могут выполняться на одном и том же рабочем станке. */
    public static boolean sharesOperationalMachine(ScheduleStore store, Capability a, Capability b) {
        return !sharedOperationalMachines(store, a, b).isEmpty();
    }

    private static boolean isEntirePreviousBatchScheduled(
            Order order, Part part, Task previous, List<Assignment> assignments) {
        for (int unit = 0; unit < part.quantity(); unit++) {
            if (!isUnitScheduled(order, part, unit, previous.taskId(), assignments)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isUnitScheduled(
            Order order, Part part, int unitIndex, String taskId, List<Assignment> assignments) {
        return AssignmentFilters.work(assignments).stream()
                .anyMatch(a -> a.orderId().equals(order.orderId())
                        && a.partId().equals(part.partId())
                        && a.unitIndex() == unitIndex
                        && a.taskId().equals(taskId)
                        && (a.isCompleted() || a.isPlanned()));
    }

    private static Instant endOfUnit(
            Order order, Part part, int unitIndex, int sequence, List<Assignment> assignments) {
        return AssignmentFilters.work(assignments).stream()
                .filter(a -> a.orderId().equals(order.orderId())
                        && a.partId().equals(part.partId())
                        && a.unitIndex() == unitIndex
                        && a.sequence() == sequence)
                .filter(a -> a.isCompleted() || a.isPlanned())
                .map(a -> a.isCompleted() ? a.effectiveEnd() : a.plannedEnd())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Missing unit " + unitIndex + " sequence " + sequence));
    }

    private static Task taskAtSequence(Part part, int sequence) {
        return part.tasks().stream()
                .filter(t -> t.sequence() == sequence)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing task sequence " + sequence));
    }

    private static List<Machine> operationalMachines(ScheduleStore store, Capability capability) {
        return store.machines().stream()
                .filter(Machine::isOperational)
                .filter(m -> m.canPerform(capability))
                .toList();
    }
}
