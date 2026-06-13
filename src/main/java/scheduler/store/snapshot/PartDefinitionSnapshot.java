package scheduler.store.snapshot;

import java.util.ArrayList;
import java.util.List;
import scheduler.model.order.Task;

/** DTO для {@code partDefinitions} в JSON. */
public class PartDefinitionSnapshot {
    public int priority;
    public List<Task> tasks = new ArrayList<>();
}
