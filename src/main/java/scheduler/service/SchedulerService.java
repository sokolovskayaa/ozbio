package scheduler.service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import scheduler.api.dto.OrderRequest;
import scheduler.api.view.ScheduleView;
import scheduler.api.view.ScheduleViewBuilder;
import scheduler.engine.machine.MachineStateSync;
import scheduler.engine.metrics.OrderProgress;
import scheduler.engine.metrics.TaskReadiness;
import scheduler.engine.planning.GreedyScheduler;
import scheduler.engine.policy.FactoryZone;
import scheduler.engine.policy.OrderIds;
import scheduler.engine.policy.OrderPriorities;
import scheduler.model.order.Order;
import scheduler.model.order.Part;
import scheduler.model.order.Task;
import scheduler.model.schedule.Assignment;
import scheduler.model.schedule.SetupIntervals;
import scheduler.store.ScheduleRepository;
import scheduler.store.core.ScheduleStore;
import scheduler.time.CurrentTimeProvider;

@Service
public class SchedulerService {
    private final ScheduleRepository repository;
    private final CurrentTimeProvider time;
    private final GreedyScheduler scheduler;

    public SchedulerService(ScheduleRepository repository, CurrentTimeProvider time) {
        this.repository = repository;
        this.time = time;
        this.scheduler = new GreedyScheduler(time);
    }

    public CurrentTimeProvider time() {
        return time;
    }

    public ScheduleView buildScheduleView() throws IOException {
        ScheduleStore store = loadSyncedState();
        return ScheduleViewBuilder.build(store, time);
    }

    @Transactional
    public synchronized AddOrderResult addOrder(OrderRequest request) throws IOException {
        ScheduleStore store = loadSyncedState();
        OrderValidator.validatePartIds(request, store);
        var parts = request.parts().stream()
                .map(line -> store.createPart(line.partId(), line.resolvedQuantity()))
                .toList();
        Instant createdAt = time.now();
        String orderId = resolveOrderId(request.orderId(), createdAt, store);
        Order order = new Order(orderId, createdAt, parts, OrderPriorities.fromCreatedAt(createdAt));
        OrderValidator.validate(order, store);

        store.addOrder(order);
        scheduler.scheduleOrder(order, store);
        verifyOrderFullyScheduled(order, store);
        repository.persistOrderScheduling(store, order.orderId());

        List<Assignment> forOrder = store.assignments().stream()
                .filter(a -> a.orderId().equals(order.orderId()))
                .toList();
        return new AddOrderResult(
                order.orderId(), OrderProgress.readyAt(order.orderId(), forOrder), forOrder);
    }

    private ScheduleStore loadSyncedState() throws IOException {
        ScheduleStore store = repository.loadState();
        MachineStateSync.sync(store, time.now());
        return store;
    }

    private void verifyOrderFullyScheduled(Order order, ScheduleStore store) {
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

    private String resolveOrderId(String requestedId, Instant createdAt, ScheduleStore store) {
        if (requestedId != null && !requestedId.isBlank()) {
            return requestedId.trim();
        }
        List<String> existing = store.orders().stream().map(Order::orderId).toList();
        return OrderIds.nextOrderId(createdAt, FactoryZone.ZONE, existing);
    }
}
