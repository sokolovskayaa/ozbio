package scheduler.service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import scheduler.api.OrderRequest;
import scheduler.engine.FactoryZone;
import scheduler.engine.GreedyScheduler;
import scheduler.engine.OrderIds;
import scheduler.engine.OrderPriorities;
import scheduler.engine.MachineStateSync;
import scheduler.engine.ScheduleMetrics;
import scheduler.model.Assignment;
import scheduler.model.Order;
import scheduler.model.Part;
import scheduler.model.SetupIntervals;
import scheduler.model.Task;
import scheduler.store.JsonScheduleRepository;
import scheduler.store.ScheduleStore;
import scheduler.time.CurrentTimeProvider;

public class SchedulerService {
    private final ScheduleStore store;
    private final JsonScheduleRepository repository;
    private final CurrentTimeProvider time;
    private final GreedyScheduler scheduler;

    public SchedulerService(
            ScheduleStore store, JsonScheduleRepository repository, CurrentTimeProvider time) {
        this.store = store;
        this.repository = repository;
        this.time = time;
        this.scheduler = new GreedyScheduler(time);
        MachineStateSync.sync(store, time.now());
    }

    public ScheduleStore store() {
        return store;
    }

    public CurrentTimeProvider time() {
        return time;
    }

    public synchronized AddOrderResult addOrder(OrderRequest request) throws IOException {
        OrderValidator.validatePartIds(request, store);
        var parts = request.parts().stream()
                .map(line -> store.createPart(line.partId(), line.resolvedQuantity()))
                .toList();
        Instant createdAt = time.now();
        String orderId = resolveOrderId(request.orderId(), createdAt);
        Order order = new Order(orderId, createdAt, parts, OrderPriorities.fromCreatedAt(createdAt));
        OrderValidator.validate(order, store);

        ScheduleStore.SchedulingSnapshot snapshot = store.captureSchedulingState();
        try {
            store.addOrder(order);
            scheduler.scheduleOrder(order, store);
            verifyOrderFullyScheduled(order);
            persist();
        } catch (Exception e) {
            store.rollbackTo(snapshot);
            throw e;
        }

        List<Assignment> forOrder = store.assignments().stream()
                .filter(a -> a.orderId().equals(order.orderId()))
                .toList();
        return new AddOrderResult(
                order.orderId(), ScheduleMetrics.readyAt(order.orderId(), forOrder), forOrder);
    }

    private void verifyOrderFullyScheduled(Order order) {
        for (Part part : order.parts()) {
            if (!ScheduleMetrics.isPartFullyScheduled(order.orderId(), part, store.assignments())) {
                throw new SchedulingException(
                        "Incomplete schedule for part " + part.partId() + " in order " + order.orderId());
            }
            for (Task task : part.tasks()) {
                if (SetupIntervals.isSetup(task.taskId())) {
                    continue;
                }
                int scheduled = ScheduleMetrics.unitsScheduledForTask(
                        order.orderId(), part.partId(), task.taskId(), store.assignments());
                if (scheduled < part.quantity()) {
                    throw new SchedulingException(
                            "Task "
                                    + task.taskId()
                                    + " scheduled for "
                                    + scheduled
                                    + " of "
                                    + part.quantity()
                                    + " units");
                }
            }
        }
    }

    private String resolveOrderId(String requestedId, Instant createdAt) {
        if (requestedId != null && !requestedId.isBlank()) {
            return requestedId.trim();
        }
        List<String> existing =
                store.orders().stream().map(Order::orderId).toList();
        return OrderIds.nextOrderId(createdAt, FactoryZone.ZONE, existing);
    }

    private void persist() throws IOException {
        repository.save(store);
    }
}
