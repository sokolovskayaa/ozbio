package scheduler.store.core;

import java.util.List;
import scheduler.model.order.Task;

/** Справочник детали при старте: приоритет и технологическая цепочка задач. */
public record PartDefinition(int priority, List<Task> tasks) {

    public PartDefinition {
        tasks = List.copyOf(tasks);
    }
}
