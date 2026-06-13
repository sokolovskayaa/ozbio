package scheduler.store.json;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import scheduler.model.schedule.Assignment;
import scheduler.model.order.Order;
import scheduler.store.snapshot.MachineGroupSnapshot;
import scheduler.store.snapshot.MachineSnapshot;
import scheduler.store.snapshot.PartDefinitionSnapshot;

/** DTO для {@code data/schedule.json}. */
public class ScheduleSnapshot {
    public Instant factoryStartedAt;
    public List<MachineSnapshot> machines = new ArrayList<>();
    public List<MachineGroupSnapshot> machineGroups = new ArrayList<>();
    public Map<String, PartDefinitionSnapshot> partDefinitions = new LinkedHashMap<>();
    public List<Order> orders = new ArrayList<>();
    public List<Assignment> assignments = new ArrayList<>();
}
