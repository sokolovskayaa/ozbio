package scheduler.engine;

import scheduler.model.Order;
import scheduler.model.Part;
import scheduler.model.Task;

public record WorkCandidate(Order order, Part part, int unitIndex, Task task) {}
