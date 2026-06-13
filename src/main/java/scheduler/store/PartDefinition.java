package scheduler.store;

import java.util.List;
import scheduler.model.Task;

/** Справочник детали при старте: приоритет и технологическая цепочка задач. */
public record PartDefinition(int priority, List<Task> tasks) {

    public PartDefinition {
        tasks = List.copyOf(tasks);
    }
}
