package scheduler.service;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import scheduler.api.dto.OrderPartRequest;
import scheduler.api.dto.OrderRequest;
import scheduler.model.order.Order;
import scheduler.model.order.Task;
import scheduler.store.PlanningRepository;

public final class OrderValidator {
    private OrderValidator() {}

    public static void validatePartIds(OrderRequest request, PlanningRepository repo) throws IOException {
        if (request.parts() == null || request.parts().isEmpty()) {
            throw new SchedulingException("Order must contain at least one part");
        }
        Set<String> seen = new HashSet<>();
        for (OrderPartRequest line : request.parts()) {
            if (line.partId() == null || line.partId().isBlank()) {
                throw new SchedulingException("partId is required");
            }
            if (!seen.add(line.partId())) {
                throw new SchedulingException("Duplicate partId in order: " + line.partId());
            }
            if (line.quantity() != null && line.quantity() < 1) {
                throw new SchedulingException("quantity must be >= 1 for part " + line.partId());
            }
            if (!repo.partExists(line.partId())) {
                throw new SchedulingException("Unknown partId in catalog: " + line.partId());
            }
            for (Task task : repo.partTasks(line.partId())) {
                if (!repo.hasOperationalMachineForCapability(task.requiredCapability())) {
                    throw new SchedulingException(
                            "No machine for capability " + task.requiredCapability() + " on part "
                                    + line.partId() + " task " + task.taskId());
                }
            }
        }
    }

    public static void validate(Order order, PlanningRepository repo) throws IOException {
        if (order.parts() == null || order.parts().isEmpty()) {
            throw new SchedulingException("Order must contain at least one part");
        }
        if (repo.orderExists(order.orderId())) {
            throw new SchedulingException("Order already exists: " + order.orderId());
        }
    }
}
