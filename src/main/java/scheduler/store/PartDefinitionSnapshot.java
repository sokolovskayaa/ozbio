package scheduler.store;

import java.util.ArrayList;
import java.util.List;
import scheduler.model.Task;

/** DTO для {@code partDefinitions} в JSON. */
public class PartDefinitionSnapshot {
    int priority;
    List<Task> tasks = new ArrayList<>();
}
