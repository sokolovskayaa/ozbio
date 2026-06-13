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

    public GreedyScheduler(CurrentTimeProvider time) {
        this.time = time;
    }

    public void scheduleOrder(Order targetOrder, ScheduleStore store) {
        MachineStateSync.sync(store, time.now());

        List<Part> partsByPriority = targetOrder.parts().stream()
                .sorted(Comparator.comparingInt((Part p) -> PartPriorities.of(store, p.partId()))
                        .reversed())
                .toList();

        while (!isOrderFullyScheduled(targetOrder, store)) {
            boolean progressed = false;

            Optional<WorkCandidate> primary = nextPrimaryCandidate(targetOrder, partsByPriority, store);
            if (primary.isPresent()) {
                Optional<PlannedWork> planned = findBestPlannedWork(primary.get(), store);
                if (planned.isPresent()
                        && isAllowedForEarlierOrders(primary.get(), planned.get(), store)
                        && isAllowedWithinOrder(primary.get(), planned.get(), store)) {
                    commitPlannedWork(planned.get(), store);
                    progressed = true;
                }
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
            Optional<PlannedWork> planned = findBestPlannedWork(candidate, store);
            if (planned.isPresent()
                    && isAllowedWithinOrder(candidate, planned.get(), store)
                    && isAllowedForEarlierOrders(candidate, planned.get(), store)) {
                commitPlannedWork(planned.get(), store);
                return true;
            }
        }
        return false;
    }

    private boolean isAllowedWithinOrder(WorkCandidate candidate, PlannedWork planned, ScheduleStore store) {
        Order order = candidate.order();
        int candidatePriority = PartPriorities.of(store, candidate.part().partId());
        Instant orderStart = ScheduleMetrics.orderStart(order, store, time);
        Map<String, Instant> before = higherPriorityPartReadyAts(
                order, candidatePriority, store.assignments(), orderStart, store);

        List<Assignment> withNew = append(store.assignments(), planned);
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

    private boolean isAllowedForEarlierOrders(WorkCandidate candidate, PlannedWork planned, ScheduleStore store) {
        for (Order other : store.orders()) {
            if (other.priority() <= candidate.order().priority()) {
                continue;
            }
            Instant before = currentReadyAt(other.orderId(), store);
            List<Assignment> withNew = append(store.assignments(), planned);
            Instant after = ScheduleMetrics.readyAt(other.orderId(), withNew);
            if (after.isAfter(before)) {
                return false;
            }
        }
        return true;
    }

    private Optional<PlannedWork> findBestPlannedWork(WorkCandidate candidate, ScheduleStore store) {
        PlannedWork best = null;
        for (Machine machine : capableMachines(candidate.task(), store)) {
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

    private Optional<PlannedWork> planWork(WorkCandidate candidate, Machine machine, ScheduleStore store) {
        Order order = candidate.order();
        Part part = candidate.part();
        Task task = candidate.task();
        int unitIndex = candidate.unitIndex();

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
        Instant machineAvailable = MachineTimeline.availableFrom(store, machine.machineId(), time.now());
        Instant anchor = max(prevEnd, orderStart, machineAvailable);

        Optional<WorkTiming> timing = planWorkTiming(anchor, setup, task.duration());
        if (timing.isEmpty()) {
            return Optional.empty();
        }
        WorkTiming planned = timing.get();
        Optional<Assignment> setupAssignment = Optional.empty();
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
        Assignment work = Assignment.planned(
                UUID.randomUUID().toString(),
                order.orderId(),
                part.partId(),
                unitIndex,
                task.taskId(),
                task.sequence(),
                machine.machineId(),
                planned.workStart(),
                planned.workEnd());

        return Optional.of(new PlannedWork(setupAssignment, work));
    }

    private static Optional<WorkTiming> planWorkTiming(Instant anchor, Duration setup, Duration work) {
        if (setup.isZero()) {
            return Optional.of(new WorkTiming(
                    Optional.empty(), Optional.empty(), anchor, anchor.plus(work)));
        }
        Instant setupStart = anchor;
        Instant setupEnd = setupStart.plus(setup);
        Instant workStart = setupEnd;
        Instant workEnd = workStart.plus(work);
        return Optional.of(new WorkTiming(
                Optional.of(setupStart), Optional.of(setupEnd), workStart, workEnd));
    }

    private void commitPlannedWork(PlannedWork planned, ScheduleStore store) {
        planned.setup().ifPresent(store::addAssignment);
        commitAssignment(planned.work(), store);
    }

    private void commitAssignment(Assignment assignment, ScheduleStore store) {
        store.addAssignment(assignment);
        store.updateMachineAvailability(assignment.machineId(), assignment.plannedEnd());
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

    private record WorkTiming(
            Optional<Instant> setupStart, Optional<Instant> setupEnd, Instant workStart, Instant workEnd) {}

    private record PlannedWork(Optional<Assignment> setup, Assignment work) {}
}
