package scheduler.service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import scheduler.api.dto.OrderRequest;
import scheduler.engine.policy.FactoryZone;
import scheduler.engine.planning.GreedyScheduler;
import scheduler.engine.policy.OrderIds;
import scheduler.engine.policy.OrderPriorities;
import scheduler.engine.machine.MachineStateSync;
import scheduler.engine.metrics.OrderProgress;
import scheduler.engine.metrics.TaskReadiness;
import scheduler.model.schedule.Assignment;
import scheduler.model.order.Order;
import scheduler.model.order.Part;
import scheduler.model.schedule.SetupIntervals;
import scheduler.model.order.Task;
import scheduler.store.json.JsonScheduleRepository;
import scheduler.store.core.ScheduleStore;
import scheduler.time.CurrentTimeProvider;
import org.springframework.stereotype.Service;

@Service
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
                order.orderId(), OrderProgress.readyAt(order.orderId(), forOrder), forOrder);
    }

    private void verifyOrderFullyScheduled(Order order) {
        for (Part part : order.parts()) {
            if (!TaskReadiness.isPartFullyScheduled(order.orderId(), part, store.assignments())) {
                throw new SchedulingException(
                        "Incomplete schedule for part " + part.partId() + " in order " + order.orderId());
            }
            for (Task task : part.tasks()) {
                if (SetupIntervals.isSetup(task.taskId())) {
                    continue;
                }
                int scheduled = TaskReadiness.unitsScheduledForTask(
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
