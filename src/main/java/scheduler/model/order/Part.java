package scheduler.model.order;

import java.util.List;

/** Приоритет детали задаётся в каталоге {@code part_definition} (БД / DemoFactoryCatalog). */
public record Part(String partId, int quantity, List<Task> tasks) {
    public Part {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be >= 1");
        }
        tasks = List.copyOf(tasks);
    }
}
