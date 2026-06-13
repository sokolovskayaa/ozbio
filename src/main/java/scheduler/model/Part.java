package scheduler.model;

import java.util.List;

/** Приоритет детали задаётся в {@code partPriorities} при старте (см. {@code ScheduleStore}). */
public record Part(String partId, int quantity, List<Task> tasks) {
    public Part {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be >= 1");
        }
        tasks = List.copyOf(tasks);
    }
}
