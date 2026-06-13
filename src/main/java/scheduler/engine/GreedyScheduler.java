package scheduler.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import scheduler.model.Assignment;
import scheduler.model.AssignmentStatus;
import scheduler.model.Machine;
import scheduler.model.MachineGroup;
import scheduler.model.Order;
import scheduler.model.Part;
import scheduler.model.SetupIntervals;
import scheduler.model.Task;
import scheduler.service.SchedulingException;
import scheduler.store.ScheduleStore;
import scheduler.time.CurrentTimeProvider;

public class GreedyScheduler {
    private final CurrentTimeProvider time;
    private final ReplanContext replanContext;

    public GreedyScheduler(CurrentTimeProvider time) {
        this(time, null);
    }

    public GreedyScheduler(CurrentTimeProvider time, ReplanContext replanContext) {
        this.time = time;
        this.replanContext = replanContext;
    }

    /**
     * Планирует заказ: для каждой детали сначала все штуки проходят операцию 1, затем все —
     * операцию 2 и т.д.; внутри операции штуки подряд (0 → 1 → 2…). Разные типы деталей
     * могут идти параллельно на разных станках, если не сдвигается готовность более
     * приоритетных деталей в заказе. Склейка одинаковых деталей между заказами не выполняется.
     */
    public void scheduleOrder(Order targetOrder, ScheduleStore store) {
        scheduleOrder(targetOrder, store, null);
    }

    public void scheduleOrder(Order targetOrder, ScheduleStore store, ReplanContext context) {
        GreedyScheduler active = context != null ? new GreedyScheduler(time, context) : this;
        active.scheduleOrderInternal(targetOrder, store);
    }

    private void scheduleOrderInternal(Order targetOrder, ScheduleStore store) {
        MachineStateSync.sync(store, time.now());

        List<Part> partsByPriority = targetOrder.parts().stream()
                .sorted(Comparator.comparingInt((Part p) -> PartPriorities.of(store, p.partId()))
                        .reversed())
                .toList();

        while (!isOrderFullyScheduled(targetOrder, store)) {
            boolean progressed = false;

            Optional<WorkCandidate> primary = nextPrimaryCandidate(targetOrder, partsByPriority, store);
            if (primary.isPresent()
                    && isAllowedForEarlierOrders(primary.get(), store)
                    && isAllowedWithinOrder(primary.get(), store)) {
                assign(primary.get(), store);
                progressed = true;
            }

            if (!progressed && tryAssignParallelLowerPriority(targetOrder, partsByPriority, store)) {
                progressed = true;
            }

            if (!progressed) {
                throw new SchedulingException(
                        "Cannot schedule order " + targetOrder.orderId());
            }
        }
    }

    private static boolean isOrderFullyScheduled(Order order, ScheduleStore store) {
        for (Part part : order.parts()) {
            if (!ScheduleMetrics.isPartFullyScheduled(order.orderId(), part, store.assignments())) {
                return false;
            }
        }
        return true;
    }

    private Optional<WorkCandidate> nextPrimaryCandidate(
            Order order, List<Part> partsByPriority, ScheduleStore store) {
        for (Part part : partsByPriority) {
            if (ScheduleMetrics.isPartFullyScheduled(order.orderId(), part, store.assignments())) {
                continue;
            }
            Instant orderStart = ScheduleMetrics.orderStart(order, store, time);
            Optional<ReadyWork> ready = ScheduleMetrics.readyWork(order, part, store.assignments(), orderStart, store);
            if (ready.isEmpty()) {
                continue;
            }
            return Optional.of(new WorkCandidate(
                    order, part, ready.get().unitIndex(), ready.get().task()));
        }
        return Optional.empty();
    }

    /** Низкоприоритетная деталь на свободном станке, если не сдвигает partReadyAt более приоритетных. */
    private boolean tryAssignParallelLowerPriority(
            Order order, List<Part> partsByPriority, ScheduleStore store) {
        Instant orderStart = ScheduleMetrics.orderStart(order, store, time);
        List<Part> byAscPriority = partsByPriority.reversed();
        for (Part part : byAscPriority) {
            if (ScheduleMetrics.isPartFullyScheduled(order.orderId(), part, store.assignments())) {
                continue;
            }
            Optional<ReadyWork> ready = ScheduleMetrics.readyWork(order, part, store.assignments(), orderStart, store);
            if (ready.isEmpty()) {
                continue;
            }
            WorkCandidate candidate =
                    new WorkCandidate(order, part, ready.get().unitIndex(), ready.get().task());
            if (!isAllowedWithinOrder(candidate, store) || !isAllowedForEarlierOrders(candidate, store)) {
                continue;
            }
            if (findBestPlannedWork(candidate, store).isPresent()) {
                assign(candidate, store);
                return true;
            }
        }
        return false;
    }

    private boolean isAllowedWithinOrder(WorkCandidate candidate, ScheduleStore store) {
        Order order = candidate.order();
        int candidatePriority = PartPriorities.of(store, candidate.part().partId());
        Instant orderStart = ScheduleMetrics.orderStart(order, store, time);
        Map<String, Instant> before = higherPriorityPartReadyAts(
                order, candidatePriority, store.assignments(), orderStart, store);

        Optional<PlannedWork> simulated = findBestPlannedWork(candidate, store);
        if (simulated.isEmpty()) {
            return false;
        }
        List<Assignment> withNew = append(store.assignments(), simulated.get());
        for (Map.Entry<String, Instant> entry : before.entrySet()) {
            Instant after = partReadyAtOrStart(
                    order.orderId(), entry.getKey(), withNew, orderStart);
            if (after.isAfter(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Instant> higherPriorityPartReadyAts(
            Order order,
            int candidatePriority,
            List<Assignment> assignments,
            Instant orderStart,
            ScheduleStore store) {
        Map<String, Instant> map = new LinkedHashMap<>();
        for (Part part : order.parts()) {
            if (PartPriorities.of(store, part.partId()) > candidatePriority) {
                map.put(
                        part.partId(),
                        partReadyAtOrStart(order.orderId(), part.partId(), assignments, orderStart));
            }
        }
        return map;
    }

    private static Instant partReadyAtOrStart(
            String orderId, String partId, List<Assignment> assignments, Instant orderStart) {
        return assignments.stream()
                .filter(a -> a.orderId().equals(orderId) && a.partId().equals(partId))
                .filter(a -> a.status() != AssignmentStatus.CANCELLED)
                .map(Assignment::effectiveEnd)
                .max(Comparator.naturalOrder())
                .orElse(orderStart);
    }

    private boolean isAllowedForEarlierOrders(WorkCandidate candidate, ScheduleStore store) {
        for (Order other : store.orders()) {
            if (other.priority() <= candidate.order().priority()) {
                continue;
            }
            Instant before = currentReadyAt(other.orderId(), store);
            Optional<PlannedWork> simulated = findBestPlannedWork(candidate, store);
            if (simulated.isEmpty()) {
                return false;
            }
            List<Assignment> withNew = append(store.assignments(), simulated.get());
            Instant after = ScheduleMetrics.readyAt(other.orderId(), withNew);
            if (after.isAfter(before)) {
                return false;
            }
        }
        return true;
    }

    private void assign(WorkCandidate candidate, ScheduleStore store) {
        PlannedWork planned = findBestPlannedWork(candidate, store)
                .orElseThrow(() -> new SchedulingException(
                        "No capable machine for task " + candidate.task().taskId()));
        commitPlannedWork(planned, store);
    }

    private Optional<PlannedWork> findBestPlannedWork(WorkCandidate candidate, ScheduleStore store) {
        PlannedWork best = null;
        List<Machine> machines = capableMachines(candidate.task(), store);
        if (replanContext != null) {
            machines = filterPreferredMachine(candidate, machines);
        }
        for (Machine machine : machines) {
            Optional<PlannedWork> tentative = planWork(candidate, machine, store);
            if (tentative.isEmpty()) {
                continue;
            }
            Assignment work = tentative.get().work();
            if (best == null
                    || work.plannedStart().isBefore(best.work().plannedStart())
                    || (work.plannedStart().equals(best.work().plannedStart())
                            && work.plannedEnd().isBefore(best.work().plannedEnd()))) {
                best = tentative.get();
            }
        }
        return Optional.ofNullable(best);
    }

    private List<Machine> filterPreferredMachine(WorkCandidate candidate, List<Machine> machines) {
        return replanContext
                .preferredMachine(
                        candidate.order().orderId(),
                        candidate.part().partId(),
                        candidate.unitIndex(),
                        candidate.task().taskId())
                .map(pref -> machines.stream().filter(m -> m.machineId().equals(pref)).toList())
                .filter(list -> !list.isEmpty())
                .orElse(machines);
    }

    private Optional<PlannedWork> planWork(WorkCandidate candidate, Machine machine, ScheduleStore store) {
        Order order = candidate.order();
        Part part = candidate.part();
        Task task = candidate.task();
        int unitIndex = candidate.unitIndex();
        MachineGroup group = store.findGroupForMachine(machine).orElse(null);

        Instant orderStart = ScheduleMetrics.orderStart(order, store, time);
        Instant prevEnd = ScheduleMetrics.previousTaskEnd(
                order,
                part,
                unitIndex,
                task.sequence(),
                store.assignments(),
                orderStart,
                store,
                machine.machineId());
        Duration setup = SetupPlanner.setupBeforeTask(machine, part.partId(), task.taskId(), store);
        Instant machineAvailable = MachineTimeline.afterBlocks(
                store, machine.machineId(), MachineTimeline.availableFrom(store, machine.machineId(), time.now()));
        Optional<WorkTiming> timing = planWorkTimingWithContiguousSetup(
                max(prevEnd, orderStart), machineAvailable, setup, task.duration(), group, store, machine.machineId());
        if (timing.isEmpty()) {
            return Optional.empty();
        }
        Optional<Assignment> setupAssignment = Optional.empty();
        WorkTiming planned = timing.get();
        if (planned.setupStart().isPresent()) {
            setupAssignment = Optional.of(Assignment.planned(
                    UUID.randomUUID().toString(),
                    order.orderId(),
                    part.partId(),
                    unitIndex,
                    SetupIntervals.TASK_ID,
                    -1,
                    machine.machineId(),
                    planned.setupStart().get(),
                    planned.setupEnd().get()));
        }
        Instant workStart = planned.workStart();
        Instant workEnd = planned.workEnd();

        Assignment work = Assignment.planned(
                UUID.randomUUID().toString(),
                order.orderId(),
                part.partId(),
                unitIndex,
                task.taskId(),
                task.sequence(),
                machine.machineId(),
                workStart,
                workEnd);

        return Optional.of(new PlannedWork(setupAssignment, work));
    }

    private void commitPlannedWork(PlannedWork planned, ScheduleStore store) {
        planned.setup().ifPresent(store::addAssignment);
        commitAssignment(planned.work(), store);
    }

    private void commitAssignment(Assignment assignment, ScheduleStore store) {
        store.addAssignment(assignment);
        store.machines().stream()
                .filter(m -> m.machineId().equals(assignment.machineId()))
                .findFirst()
                .ifPresent(m -> {
                    if (assignment.plannedEnd().isAfter(m.availableAt())) {
                        m.setAvailableAt(assignment.plannedEnd());
                    }
                });
    }

    private List<Machine> capableMachines(Task task, ScheduleStore store) {
        return store.machines().stream()
                .filter(Machine::isOperational)
                .filter(m -> m.canPerform(task.requiredCapability()))
                .sorted(Comparator.comparing(Machine::availableAt))
                .toList();
    }

    private Instant currentReadyAt(String orderId, ScheduleStore store) {
        List<Assignment> existing = store.assignments().stream()
                .filter(a -> a.orderId().equals(orderId))
                .toList();
        if (existing.isEmpty()) {
            return store.factoryStartedAt();
        }
        return ScheduleMetrics.readyAt(orderId, existing);
    }

    private static Instant max(Instant... values) {
        Instant result = values[0];
        for (int i = 1; i < values.length; i++) {
            if (values[i].isAfter(result)) {
                result = values[i];
            }
        }
        return result;
    }

    private static List<Assignment> append(List<Assignment> list, PlannedWork planned) {
        List<Assignment> copy = new ArrayList<>(list);
        planned.setup().ifPresent(copy::add);
        copy.add(planned.work());
        return copy;
    }

    /**
     * Старт обработки не раньше {@code productionEarliest}; переналадка заканчивается
     * ровно в {@code workStart} (без разрыва). Планирование вперёд: в одной смене укладываются
     * переналадка + работа (обратный {@code subtractWorkDuration} давал start &gt; end).
     */
    private static Optional<WorkTiming> planWorkTimingWithContiguousSetup(
            Instant productionEarliest,
            Instant machineAvailable,
            Duration setup,
            Duration work,
            MachineGroup group,
            ScheduleStore store,
            String machineId) {
        java.time.ZoneId zone = FactoryZone.ZONE;
        Instant anchor = MachineTimeline.afterBlocks(
                store, machineId, max(productionEarliest, machineAvailable));
        if (setup.isZero()) {
            Optional<Instant> workStart = ShiftCalendar.nextShiftStartFittingDuration(anchor, work, group, zone);
            if (workStart.isEmpty()) {
                return Optional.empty();
            }
            Instant start = workStart.get();
            if (start.isBefore(productionEarliest)) {
                Optional<Instant> later = ShiftCalendar.nextShiftStartFittingDuration(
                        productionEarliest, work, group, zone);
                if (later.isEmpty()) {
                    return Optional.empty();
                }
                start = later.get();
            }
            return Optional.of(new WorkTiming(
                    Optional.empty(),
                    Optional.empty(),
                    start,
                    ShiftCalendar.addWorkDuration(start, work, group, zone)));
        }
        Duration block = setup.plus(work);
        Instant cursor = anchor;
        for (int guard = 0; guard < 500; guard++) {
            Instant searchFrom = MachineTimeline.afterBlocks(store, machineId, max(cursor, machineAvailable));
            Optional<Instant> setupStartOpt =
                    ShiftCalendar.nextShiftStartFittingDuration(searchFrom, block, group, zone);
            if (setupStartOpt.isEmpty()) {
                return Optional.empty();
            }
            Instant setupStart = setupStartOpt.get();
            Instant setupEnd = ShiftCalendar.addWorkDuration(setupStart, setup, group, zone);
            Instant workStart = setupEnd;
            if (workStart.isBefore(productionEarliest)) {
                cursor = ShiftCalendar.nextWorkStart(productionEarliest, group, zone);
                continue;
            }
            Instant workEnd = ShiftCalendar.addWorkDuration(workStart, work, group, zone);
            if (!setupStart.isBefore(setupEnd) || workStart.isAfter(workEnd)) {
                cursor = ShiftCalendar.nextWorkStart(
                        ShiftCalendar.currentShiftEnd(setupStart, group, zone), group, zone);
                continue;
            }
            return Optional.of(new WorkTiming(
                    Optional.of(setupStart), Optional.of(setupEnd), workStart, workEnd));
        }
        return Optional.empty();
    }

    private record WorkTiming(
            Optional<Instant> setupStart, Optional<Instant> setupEnd, Instant workStart, Instant workEnd) {}

    private record PlannedWork(Optional<Assignment> setup, Assignment work) {}
}
