package scheduler.store;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import scheduler.model.Assignment;
import scheduler.model.Order;

/** DTO для {@code data/schedule.json}. */
public class ScheduleSnapshot {
    Instant factoryStartedAt;
    List<MachineSnapshot> machines = new ArrayList<>();
    List<MachineGroupSnapshot> machineGroups = new ArrayList<>();
    Map<String, PartDefinitionSnapshot> partDefinitions = new LinkedHashMap<>();
    List<Order> orders = new ArrayList<>();
    List<Assignment> assignments = new ArrayList<>();
}
