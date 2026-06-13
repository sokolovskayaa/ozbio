package scheduler.engine.planning;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import scheduler.engine.machine.MachineTimeline;
import scheduler.engine.metrics.OrderProgress;
import scheduler.engine.metrics.TaskReadiness;
import scheduler.engine.policy.PartPriorities;
import scheduler.model.schedule.Assignment;
import scheduler.model.schedule.AssignmentStatus;
import scheduler.model.machine.Machine;
import scheduler.model.order.Order;
import scheduler.model.order.Part;
import scheduler.model.schedule.SetupIntervals;
import scheduler.model.order.Task;
import scheduler.service.SchedulingException;
import scheduler.store.PlanningRepository;
import scheduler.time.CurrentTimeProvider;

public class GreedyScheduler {
    private final CurrentTimeProvider time;

    public GreedyScheduler(CurrentTimeProvider time) {
        this.time = time;
    }

    public void scheduleOrder(Order targetOrder, PlanningRepository repo, List<Assignment> sessionAssignments)
            throws java.io.IOException {
        repo.syncOperationalMachines(time.now());
        Instant factoryStartedAt = repo.factoryStartedAt();

        Map<String, Integer> partPriorities = new LinkedHashMap<>();
        for (Part part : targetOrder.parts()) {
            partPriorities.put(part.partId(), PartPriorities.of(repo, part.partId()));
        }
        List<Part> partsByPriority = targetOrder.parts().stream()
                .sorted(Comparator.comparingInt((Part p) -> partPriorities.get(p.partId())).reversed())
                .toList();

        while (!isOrderFullyScheduled(targetOrder, sessionAssignments)) {
            boolean progressed = false;

            Optional<WorkCandidate> primary = nextPrimaryCandidate(
                    targetOrder, partsByPriority, sessionAssignments, factoryStartedAt);
            if (primary.isPresent()) {
                Optional<PlannedWork> planned = findBestPlannedWork(primary.get(), repo, sessionAssignments, factoryStartedAt);
                if (planned.isPresent()
                        && isAllowedForEarlierOrders(primary.get(), planned.get(), repo)
                        && isAllowedWithinOrder(
                                primary.get(), planned.get(), repo, sessionAssignments, factoryStartedAt)) {
                    commitPlannedWork(planned.get(), repo, sessionAssignments);
                    progressed = true;
                }
            }

            if (!progressed && tryAssignParallelLowerPriority(
                    targetOrder, partsByPriority, repo, sessionAssignments, factoryStartedAt)) {
                progressed = true;
            }

            if (!progressed) {
                throw new SchedulingException(
                        "Cannot schedule order " + targetOrder.orderId());
            }
        }
    }

    private static boolean isOrderFullyScheduled(Order order, List<Assignment> assignments) {
        for (Part part : order.parts()) {
            if (!TaskReadiness.isPartFullyScheduled(order.orderId(), part, assignments)) {
                return false;
            }
        }
        return true;
    }

    private Optional<WorkCandidate> nextPrimaryCandidate(
            Order order,
            List<Part> partsByPriority,
            List<Assignment> assignments,
            Instant factoryStartedAt) {
        for (Part part : partsByPriority) {
            if (TaskReadiness.isPartFullyScheduled(order.orderId(), part, assignments)) {
                continue;
            }
            Instant orderStart = OrderProgress.orderStart(order, factoryStartedAt, time);
            Optional<ReadyWork> ready = TaskReadiness.readyWork(order, part, assignments, orderStart);
            if (ready.isEmpty()) {
                continue;
            }
            return Optional.of(new WorkCandidate(
                    order, part, ready.get().unitIndex(), ready.get().task()));
        }
        return Optional.empty();
    }

    private boolean tryAssignParallelLowerPriority(
            Order order,
            List<Part> partsByPriority,
            PlanningRepository repo,
            List<Assignment> assignments,
            Instant factoryStartedAt) throws java.io.IOException {
        Instant orderStart = OrderProgress.orderStart(order, factoryStartedAt, time);
        List<Part> byAscPriority = partsByPriority.reversed();
        for (Part part : byAscPriority) {
            if (TaskReadiness.isPartFullyScheduled(order.orderId(), part, assignments)) {
                continue;
            }
            Optional<ReadyWork> ready = TaskReadiness.readyWork(order, part, assignments, orderStart);
            if (ready.isEmpty()) {
                continue;
            }
            WorkCandidate candidate =
                    new WorkCandidate(order, part, ready.get().unitIndex(), ready.get().task());
            Optional<PlannedWork> planned = findBestPlannedWork(candidate, repo, assignments, factoryStartedAt);
            if (planned.isPresent()
                    && isAllowedWithinOrder(candidate, planned.get(), repo, assignments, factoryStartedAt)
                    && isAllowedForEarlierOrders(candidate, planned.get(), repo)) {
                commitPlannedWork(planned.get(), repo, assignments);
                return true;
            }
        }
        return false;
    }

    private boolean isAllowedWithinOrder(
            WorkCandidate candidate,
            PlannedWork planned,
            PlanningRepository repo,
            List<Assignment> assignments,
            Instant factoryStartedAt) throws java.io.IOException {
        Order order = candidate.order();
        int candidatePriority = PartPriorities.of(repo, candidate.part().partId());
        Instant orderStart = OrderProgress.orderStart(order, factoryStartedAt, time);
        Map<String, Instant> before = higherPriorityPartReadyAts(
                order, candidatePriority, repo, assignments, orderStart);

        List<Assignment> withNew = append(assignments, planned);
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
            PlanningRepository repo,
            List<Assignment> assignments,
            Instant orderStart) throws java.io.IOException {
        Map<String, Instant> map = new LinkedHashMap<>();
        for (Part part : order.parts()) {
            if (PartPriorities.of(repo, part.partId()) > candidatePriority) {
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

    private boolean isAllowedForEarlierOrders(
            WorkCandidate candidate, PlannedWork planned, PlanningRepository repo) throws java.io.IOException {
        for (Order other : repo.ordersWithPriorityAbove(candidate.order().priority())) {
            Instant before = repo.orderReadyAt(other.orderId()).orElse(repo.factoryStartedAt());
            List<Assignment> withNew = append(repo.assignmentsForOrder(other.orderId()), planned);
            Optional<Instant> afterOpt = withNew.stream()
                    .filter(a -> a.orderId().equals(other.orderId()))
                    .filter(a -> a.status() != AssignmentStatus.CANCELLED)
                    .map(Assignment::effectiveEnd)
                    .max(Comparator.naturalOrder());
            Instant after = afterOpt.orElse(repo.factoryStartedAt());
            if (after.isAfter(before)) {
                return false;
            }
        }
        return true;
    }

    private Optional<PlannedWork> findBestPlannedWork(
            WorkCandidate candidate,
            PlanningRepository repo,
            List<Assignment> assignments,
            Instant factoryStartedAt) throws java.io.IOException {
        PlannedWork best = null;
        for (Machine machine : repo.findOperationalMachines(candidate.task().requiredCapability())) {
            Optional<PlannedWork> tentative = planWork(candidate, machine, repo, assignments, factoryStartedAt);
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

    private Optional<PlannedWork> planWork(
            WorkCandidate candidate,
            Machine machine,
            PlanningRepository repo,
            List<Assignment> assignments,
            Instant factoryStartedAt) throws java.io.IOException {
        Order order = candidate.order();
        Part part = candidate.part();
        Task task = candidate.task();
        int unitIndex = candidate.unitIndex();

        Instant orderStart = OrderProgress.orderStart(order, factoryStartedAt, time);
        Instant prevEnd = TaskReadiness.previousTaskEnd(
                order,
                part,
                unitIndex,
                task.sequence(),
                assignments,
                orderStart,
                machine.machineId());
        Duration setup = SetupPlanner.setupBeforeTask(machine, part.partId(), task.taskId(), repo);
        Instant machineAvailable = MachineTimeline.availableFrom(repo, machine.machineId(), time.now());
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

    private void commitPlannedWork(
            PlannedWork planned, PlanningRepository repo, List<Assignment> sessionAssignments)
            throws java.io.IOException {
        if (planned.setup().isPresent()) {
            Assignment setup = planned.setup().get();
            repo.insertAssignment(setup);
            sessionAssignments.add(setup);
            repo.updateMachineAvailableAt(setup.machineId(), setup.plannedEnd());
        }
        commitAssignment(planned.work(), repo, sessionAssignments);
    }

    private void commitAssignment(Assignment assignment, PlanningRepository repo, List<Assignment> sessionAssignments)
            throws java.io.IOException {
        repo.insertAssignment(assignment);
        sessionAssignments.add(assignment);
        repo.updateMachineAvailableAt(assignment.machineId(), assignment.plannedEnd());
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
