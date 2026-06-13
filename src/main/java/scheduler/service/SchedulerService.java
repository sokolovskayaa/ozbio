package scheduler.service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import scheduler.api.dto.OrderRequest;
import scheduler.api.view.ScheduleView;
import scheduler.api.view.ScheduleViewBuilder;
import scheduler.engine.metrics.OrderProgress;
import scheduler.engine.metrics.TaskReadiness;
import scheduler.engine.policy.FactoryZone;
import scheduler.engine.policy.OrderIds;
import scheduler.engine.policy.OrderPriorities;
import scheduler.engine.planning.GreedyScheduler;
import scheduler.model.order.Order;
import scheduler.model.order.Part;
import scheduler.model.order.Task;
import scheduler.model.schedule.Assignment;
import scheduler.model.schedule.SetupIntervals;
import scheduler.store.PlanningRepository;
import scheduler.store.ScheduleQueryRepository;
import scheduler.time.CurrentTimeProvider;

@Service
public class SchedulerService {
    private final ScheduleQueryRepository queryRepository;
    private final PlanningRepository planningRepository;
    private final CurrentTimeProvider time;
    private final GreedyScheduler scheduler;

    public SchedulerService(
            ScheduleQueryRepository queryRepository,
            PlanningRepository planningRepository,
            CurrentTimeProvider time) {
        this.queryRepository = queryRepository;
        this.planningRepository = planningRepository;
        this.time = time;
        this.scheduler = new GreedyScheduler(time);
    }

    public CurrentTimeProvider time() {
        return time;
    }

    public ScheduleView buildScheduleView() throws IOException {
        return ScheduleViewBuilder.build(queryRepository.loadScheduleData(), time);
    }

    @Transactional
    public synchronized AddOrderResult addOrder(OrderRequest request) throws IOException {
        OrderValidator.validatePartIds(request, planningRepository);
        List<Part> parts = new ArrayList<>();
        for (var line : request.parts()) {
            parts.add(new Part(
                    line.partId(),
                    line.resolvedQuantity(),
                    planningRepository.partTasks(line.partId())));
        }
        Instant createdAt = time.now();
        String orderId = resolveOrderId(request.orderId(), createdAt);
        Order order = new Order(orderId, createdAt, parts, OrderPriorities.fromCreatedAt(createdAt));
        OrderValidator.validate(order, planningRepository);

        planningRepository.insertOrder(order);
        List<Assignment> sessionAssignments = new ArrayList<>();
        scheduler.scheduleOrder(order, planningRepository, sessionAssignments);
        verifyOrderFullyScheduled(order, sessionAssignments);

        return new AddOrderResult(
                order.orderId(), OrderProgress.readyAt(order.orderId(), sessionAssignments), sessionAssignments);
    }

    private void verifyOrderFullyScheduled(Order order, List<Assignment> assignments) {
        for (Part part : order.parts()) {
            if (!TaskReadiness.isPartFullyScheduled(order.orderId(), part, assignments)) {
                throw new SchedulingException(
                        "Incomplete schedule for part " + part.partId() + " in order " + order.orderId());
            }
            for (Task task : part.tasks()) {
                if (SetupIntervals.isSetup(task.taskId())) {
                    continue;
                }
                int scheduled = TaskReadiness.unitsScheduledForTask(
                        order.orderId(), part.partId(), task.taskId(), assignments);
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

    private String resolveOrderId(String requestedId, Instant createdAt) throws IOException {
        if (requestedId != null && !requestedId.isBlank()) {
            return requestedId.trim();
        }
        List<String> existing = planningRepository.listOrderIds();
        return OrderIds.nextOrderId(createdAt, FactoryZone.ZONE, existing);
    }
}
