package scheduler.engine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import scheduler.model.Assignment;
import scheduler.model.AssignmentStatus;
import scheduler.model.Order;
import scheduler.model.SetupIntervals;
import scheduler.service.SchedulingException;
import scheduler.store.ScheduleStore;
import scheduler.time.CurrentTimeProvider;

public final class ReplanService {
    private final GreedyScheduler scheduler;
    private final CurrentTimeProvider time;

    public ReplanService(CurrentTimeProvider time) {
        this.time = time;
        this.scheduler = new GreedyScheduler(time);
    }

    public ReplanResult replan(ScheduleStore store, Set<String> affectedOrderIds) {
        Instant now = time.now();
        List<String> ordered = store.orders().stream()
                .map(Order::orderId)
                .filter(affectedOrderIds::contains)
                .toList();

        ReplanContext context = buildPreferredMachines(store, affectedOrderIds);

        int cancelled = 0;
        for (String orderId : ordered) {
            cancelled += store.cancelPlannedForOrder(orderId);
        }

        MachineStateSync.sync(store, now);

        for (String orderId : ordered) {
            Order order = store.findOrder(orderId)
                    .orElseThrow(() -> new SchedulingException("Unknown order: " + orderId));
            scheduler.scheduleOrder(order, store, context);
            verifyOrderFullyScheduled(order, store);
        }

        MachineStateSync.sync(store, now);
        return new ReplanResult(cancelled, List.copyOf(ordered));
    }

    private static ReplanContext buildPreferredMachines(ScheduleStore store, Set<String> affectedOrderIds) {
        ReplanContext context = new ReplanContext();
        for (Assignment a : store.assignments()) {
            if (!affectedOrderIds.contains(a.orderId()) || SetupIntervals.isSetup(a.taskId())) {
                continue;
            }
            if (a.isPlanned() || a.isCompleted()) {
                context.rememberMachine(a.orderId(), a.partId(), a.unitIndex(), a.taskId(), a.machineId());
            }
        }
        return context;
    }

    private static void verifyOrderFullyScheduled(Order order, ScheduleStore store) {
        for (var part : order.parts()) {
            if (!ScheduleMetrics.isPartFullyScheduled(order.orderId(), part, store.assignments())) {
                throw new SchedulingException(
                        "Incomplete schedule after replan for part " + part.partId() + " in " + order.orderId());
            }
        }
    }

    public static Set<String> ordersAffectedByFacts(ScheduleStore store) {
        Set<String> ids = new LinkedHashSet<>();
        for (Assignment a : store.assignments()) {
            if (a.status() == AssignmentStatus.CANCELLED || a.isCompleted()) {
                ids.add(a.orderId());
            }
        }
        return ids;
    }

    public record ReplanResult(int cancelledAssignments, List<String> replannedOrderIds) {}
}
