package scheduler.service;

import java.util.HashSet;
import java.util.Set;
import scheduler.api.OrderPartRequest;
import scheduler.api.OrderRequest;
import scheduler.model.Machine;
import scheduler.model.Order;
import scheduler.model.Task;
import scheduler.store.ScheduleStore;

public final class OrderValidator {
    private OrderValidator() {}

    public static void validatePartIds(OrderRequest request, ScheduleStore store) {
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
            if (!store.hasPartDefinition(line.partId())) {
                throw new SchedulingException("Unknown partId in catalog: " + line.partId());
            }
            for (Task task : store.partTasks(line.partId())) {
                boolean hasMachine = store.machines().stream()
                        .filter(Machine::isOperational)
                        .anyMatch(m -> m.canPerform(task.requiredCapability()));
                if (!hasMachine) {
                    throw new SchedulingException(
                            "No machine for capability " + task.requiredCapability() + " on part "
                                    + line.partId() + " task " + task.taskId());
                }
            }
        }
    }

    public static void validate(Order order, ScheduleStore store) {
        if (order.parts() == null || order.parts().isEmpty()) {
            throw new SchedulingException("Order must contain at least one part");
        }
        if (store.hasOrder(order.orderId())) {
            throw new SchedulingException("Order already exists: " + order.orderId());
        }
    }
}
