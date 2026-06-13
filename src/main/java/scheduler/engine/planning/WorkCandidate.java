package scheduler.engine.planning;

import scheduler.model.order.Order;
import scheduler.model.order.Part;
import scheduler.model.order.Task;

public record WorkCandidate(Order order, Part part, int unitIndex, Task task) {}
