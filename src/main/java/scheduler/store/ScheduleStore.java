package scheduler.store;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import scheduler.model.Assignment;
import scheduler.model.AssignmentStatus;
import scheduler.model.MachineBlock;
import scheduler.model.Capability;
import scheduler.model.Machine;
import scheduler.model.MachineGroup;
import scheduler.model.MachineGroupDefaults;
import scheduler.model.MachineStatus;
import scheduler.engine.AssignmentFilters;
import scheduler.engine.OrderPriorities;
import scheduler.model.Order;
import scheduler.model.SetupIntervals;
import scheduler.model.Part;
import scheduler.model.Task;
import scheduler.model.WorkWindow;

public class ScheduleStore {
    private Instant factoryStartedAt;
    private boolean simulationClockEnabled;
    private Instant simulationCurrentTime;
    private final List<Order> orders = new ArrayList<>();
    private final List<Assignment> assignments = new ArrayList<>();
    private final List<MachineBlock> machineBlocks = new ArrayList<>();
    private final List<Machine> machines = new ArrayList<>();
    private final Map<String, MachineGroup> machineGroups = new LinkedHashMap<>();
    private final Map<String, PartDefinition> partDefinitions = new LinkedHashMap<>();
    private final Map<String, Instant> lastClosedShiftEndByGroup = new LinkedHashMap<>();
    private boolean overlapBatchesEnabled = false;

    public static ScheduleStore empty(Instant factoryStartedAt, boolean simulationEnabled, Instant simulationTime) {
        ScheduleStore store = new ScheduleStore();
        store.factoryStartedAt = factoryStartedAt;
        store.simulationClockEnabled = simulationEnabled;
        store.simulationCurrentTime = simulationTime;
        store.machineGroups.putAll(defaultMachineGroups());
        store.machines.addAll(defaultMachines(factoryStartedAt));
        return store;
    }

    public static ScheduleStore fromSnapshot(ScheduleSnapshot snapshot) {
        ScheduleStore store = new ScheduleStore();
        store.factoryStartedAt = snapshot.factoryStartedAt;
        var clock = snapshot.simulationClock;
        store.simulationClockEnabled = clock == null || clock.enabled;
        store.simulationCurrentTime = clock != null && clock.currentTime != null
                ? clock.currentTime
                : snapshot.factoryStartedAt;
        if (snapshot.machineGroups != null) {
            snapshot.machineGroups.forEach(g -> {
                if (g != null && g.groupId != null) {
                    store.machineGroups.put(g.groupId, g.toGroup());
                }
            });
        }
        if (store.machineGroups.isEmpty()) {
            store.machineGroups.putAll(defaultMachineGroups());
        }
        if (snapshot.machines != null) {
            snapshot.machines.forEach(m -> store.machines.add(m.toMachine(defaultGroupForMachine(m.machineId()))));
        }
        if (snapshot.orders != null) {
            snapshot.orders.stream().map(ScheduleStore::normalizeOrder).forEach(store.orders::add);
            store.sortOrders();
        }
        if (snapshot.assignments != null) {
            snapshot.assignments.stream()
                    .map(AssignmentNormalization::normalize)
                    .forEach(store.assignments::add);
        }
        if (snapshot.machineBlocks != null) {
            store.machineBlocks.addAll(snapshot.machineBlocks);
        }
        if (snapshot.lastClosedShiftEndByGroup != null) {
            store.lastClosedShiftEndByGroup.putAll(snapshot.lastClosedShiftEndByGroup);
        }
        store.overlapBatchesEnabled = resolveOverlapBatches(snapshot);
        if (snapshot.partDefinitions != null) {
            snapshot.partDefinitions.forEach((id, def) -> {
                if (def != null && def.tasks != null) {
                    store.partDefinitions.put(id, new PartDefinition(def.priority, def.tasks));
                }
            });
        }
        if (store.machines.isEmpty()) {
            store.machines.addAll(defaultMachines(store.factoryStartedAt));
        }
        return store;
    }

    public ScheduleSnapshot toSnapshot() {
        ScheduleSnapshot snapshot = new ScheduleSnapshot();
        snapshot.factoryStartedAt = factoryStartedAt;
        snapshot.simulationClock.enabled = simulationClockEnabled;
        snapshot.simulationClock.currentTime = simulationCurrentTime;
        snapshot.machines = machines.stream().map(MachineSnapshot::from).toList();
        snapshot.machineGroups =
                machineGroups.values().stream().map(MachineGroupSnapshot::from).toList();
        snapshot.partDefinitions = new LinkedHashMap<>();
        partDefinitions.forEach((id, def) -> {
            PartDefinitionSnapshot dto = new PartDefinitionSnapshot();
            dto.priority = def.priority();
            dto.tasks = def.tasks();
            snapshot.partDefinitions.put(id, dto);
        });
        snapshot.orders = List.copyOf(orders);
        snapshot.assignments = List.copyOf(assignments);
        snapshot.machineBlocks = List.copyOf(machineBlocks);
        snapshot.lastClosedShiftEndByGroup = new LinkedHashMap<>(lastClosedShiftEndByGroup);
        snapshot.scheduling.overlapBatches = overlapBatchesEnabled;
        return snapshot;
    }

    private static boolean resolveOverlapBatches(ScheduleSnapshot snapshot) {
        String property = System.getProperty("scheduler.overlapBatches");
        if (property != null) {
            return Boolean.parseBoolean(property);
        }
        if (snapshot.scheduling != null) {
            return snapshot.scheduling.overlapBatches;
        }
        return false;
    }

    public Instant lastClosedShiftEnd(String groupId) {
        return lastClosedShiftEndByGroup.get(groupId);
    }

    public Map<String, Instant> lastClosedShiftEndByGroup() {
        return Map.copyOf(lastClosedShiftEndByGroup);
    }

    public void setLastClosedShiftEnd(String groupId, Instant shiftEnd) {
        if (groupId == null || groupId.isBlank()) {
            throw new IllegalArgumentException("groupId required");
        }
        if (shiftEnd == null) {
            throw new IllegalArgumentException("shiftEnd required");
        }
        lastClosedShiftEndByGroup.put(groupId, shiftEnd);
    }

    public static Map<String, MachineGroup> defaultMachineGroups() {
        Map<String, MachineGroup> groups = new LinkedHashMap<>();
        groups.put(
                "cnc",
                new MachineGroup(
                        "cnc",
                        "ЧПУ (фрезерный и токарный)",
                        weekdayShift(DayOfWeek.MONDAY, DayOfWeek.FRIDAY, LocalTime.of(8, 0), LocalTime.of(20, 0)),
                        MachineGroupDefaults.setupDuration("cnc")));
        groups.put(
                "heavy",
                new MachineGroup(
                        "heavy",
                        "Тяжёлое оборудование (расточка, шлифование)",
                        weekdayShift(DayOfWeek.MONDAY, DayOfWeek.FRIDAY, LocalTime.of(7, 0), LocalTime.of(19, 0)),
                        MachineGroupDefaults.setupDuration("heavy")));
        groups.put(
                "finish",
                new MachineGroup(
                        "finish",
                        "Сварка и сборка",
                        weekdayShift(DayOfWeek.MONDAY, DayOfWeek.FRIDAY, LocalTime.of(8, 0), LocalTime.of(17, 0)),
                        MachineGroupDefaults.setupDuration("finish")));
        return groups;
    }

    private static List<WorkWindow> weekdayShift(
            DayOfWeek from, DayOfWeek to, LocalTime start, LocalTime end) {
        List<WorkWindow> windows = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            if (day.compareTo(from) >= 0 && day.compareTo(to) <= 0) {
                windows.add(new WorkWindow(day, start, end));
            }
        }
        return List.copyOf(windows);
    }

    private static String defaultGroupForMachine(String machineId) {
        return switch (machineId) {
            case "ФРЕЗ-ЧПУ-01", "ТОКАР-ЧПУ-02" -> "cnc";
            case "РАСТОЧ-03", "ШЛИФ-04" -> "heavy";
            case "СВАРКА-05", "СБОРКА-06" -> "finish";
            default -> "cnc";
        };
    }

    private static List<Machine> defaultMachines(Instant factoryStartedAt) {
        return List.of(
                new Machine(
                        "ФРЕЗ-ЧПУ-01",
                        "cnc",
                        java.util.Set.of(Capability.MILLING),
                        factoryStartedAt,
                        MachineStatus.IDLE),
                new Machine(
                        "ТОКАР-ЧПУ-02",
                        "cnc",
                        java.util.Set.of(Capability.TURNING),
                        factoryStartedAt,
                        MachineStatus.IDLE),
                new Machine(
                        "РАСТОЧ-03",
                        "heavy",
                        java.util.Set.of(Capability.DEEP_BORING),
                        factoryStartedAt,
                        MachineStatus.IDLE),
                new Machine(
                        "ШЛИФ-04",
                        "heavy",
                        java.util.Set.of(Capability.GRINDING),
                        factoryStartedAt,
                        MachineStatus.IDLE),
                new Machine(
                        "СВАРКА-05",
                        "finish",
                        java.util.Set.of(Capability.WELDING),
                        factoryStartedAt,
                        MachineStatus.IDLE),
                new Machine(
                        "СБОРКА-06",
                        "finish",
                        java.util.Set.of(Capability.ASSEMBLY),
                        factoryStartedAt,
                        MachineStatus.IDLE));
    }

    public Map<String, MachineGroup> machineGroups() {
        return Map.copyOf(machineGroups);
    }

    public MachineGroup findMachineGroup(String groupId) {
        MachineGroup group = machineGroups.get(groupId);
        if (group == null) {
            throw new IllegalArgumentException("Unknown machine group: " + groupId);
        }
        return group;
    }

    public Optional<MachineGroup> findGroupForMachine(Machine machine) {
        return Optional.ofNullable(machineGroups.get(machine.groupId()));
    }

    public void setMachineGroup(MachineGroup group) {
        machineGroups.put(group.groupId(), group);
    }

    public Instant factoryStartedAt() {
        return factoryStartedAt;
    }

    public boolean simulationClockEnabled() {
        return simulationClockEnabled;
    }

    public void setSimulationClockEnabled(boolean simulationClockEnabled) {
        this.simulationClockEnabled = simulationClockEnabled;
    }

    public Instant simulationCurrentTime() {
        return simulationCurrentTime;
    }

    public void setSimulationCurrentTime(Instant simulationCurrentTime) {
        this.simulationCurrentTime = simulationCurrentTime;
    }

    public boolean overlapBatchesEnabled() {
        return overlapBatchesEnabled;
    }

    public void setOverlapBatchesEnabled(boolean overlapBatchesEnabled) {
        this.overlapBatchesEnabled = overlapBatchesEnabled;
    }

    public List<Order> orders() {
        return List.copyOf(orders);
    }

    public List<Assignment> assignments() {
        return List.copyOf(assignments);
    }

    public List<Machine> machines() {
        return machines;
    }

    public void addOrder(Order order) {
        orders.add(order);
        sortOrders();
    }

    public void addAssignment(Assignment assignment) {
        assignments.add(assignment);
    }

    public void replaceAssignment(Assignment assignment) {
        for (int i = 0; i < assignments.size(); i++) {
            if (assignments.get(i).assignmentId().equals(assignment.assignmentId())) {
                assignments.set(i, assignment);
                return;
            }
        }
        assignments.add(assignment);
    }

    public List<MachineBlock> machineBlocks() {
        return List.copyOf(machineBlocks);
    }

    public void addMachineBlock(MachineBlock block) {
        machineBlocks.add(block);
    }

    public void clearMachineBlocks() {
        machineBlocks.clear();
    }

    public Optional<Assignment> findPlannedWorkAssignment(
            String orderId, String partId, int unitIndex, String taskId) {
        return AssignmentFilters.work(assignments).stream()
                .filter(a -> a.orderId().equals(orderId)
                        && a.partId().equals(partId)
                        && a.unitIndex() == unitIndex
                        && a.taskId().equals(taskId)
                        && a.isPlanned())
                .findFirst();
    }

    public Optional<Assignment> findPlannedSetupForUnit(
            String orderId, String partId, int unitIndex, String machineId) {
        return assignments.stream()
                .filter(a -> SetupIntervals.isSetup(a.taskId()))
                .filter(a -> a.orderId().equals(orderId)
                        && a.partId().equals(partId)
                        && a.unitIndex() == unitIndex
                        && a.machineId().equals(machineId)
                        && a.isPlanned())
                .findFirst();
    }

    public int cancelPlannedForOrder(String orderId) {
        int count = 0;
        for (int i = 0; i < assignments.size(); i++) {
            Assignment a = assignments.get(i);
            if (!a.orderId().equals(orderId) || !a.isPlanned()) {
                continue;
            }
            assignments.set(i, cancel(a));
            count++;
        }
        return count;
    }

    private static Assignment cancel(Assignment a) {
        return new Assignment(
                a.assignmentId(),
                a.orderId(),
                a.partId(),
                a.unitIndex(),
                a.taskId(),
                a.sequence(),
                a.machineId(),
                a.plannedStart(),
                a.plannedEnd(),
                AssignmentStatus.CANCELLED,
                a.actualStart(),
                a.actualEnd());
    }

    public Optional<Order> findOrder(String orderId) {
        return orders.stream().filter(o -> o.orderId().equals(orderId)).findFirst();
    }

    public boolean hasOrder(String orderId) {
        return orders.stream().anyMatch(o -> o.orderId().equals(orderId));
    }

    public Machine findMachine(String machineId) {
        return machines.stream()
                .filter(m -> m.machineId().equals(machineId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown machine: " + machineId));
    }

    public boolean hasPartDefinition(String partId) {
        return partDefinitions.containsKey(partId);
    }

    public int partPriority(String partId) {
        return definition(partId).priority();
    }

    public List<Task> partTasks(String partId) {
        return definition(partId).tasks();
    }

    private static Order normalizeOrder(Order order) {
        List<Part> parts = order.parts().stream()
                .map(p -> p.quantity() < 1 ? new Part(p.partId(), 1, p.tasks()) : p)
                .toList();
        int priority = OrderPriorities.resolve(order.createdAt(), order.priority());
        return new Order(order.orderId(), order.createdAt(), parts, priority);
    }

    private void sortOrders() {
        orders.sort(OrderPriorities.QUEUE_ORDER);
    }

    /** Деталь заказа из справочника (копия цепочки задач). */
    public Part createPart(String partId, int quantity) {
        PartDefinition def = definition(partId);
        return new Part(partId, quantity, def.tasks());
    }

    public Map<String, Integer> partPriorities() {
        Map<String, Integer> map = new LinkedHashMap<>();
        partDefinitions.forEach((id, def) -> map.put(id, def.priority()));
        return Map.copyOf(map);
    }

    public Map<String, PartDefinition> partDefinitions() {
        return Map.copyOf(partDefinitions);
    }

    public void setPartDefinition(String partId, PartDefinition definition) {
        validateTasks(partId, definition.tasks());
        partDefinitions.put(partId, definition);
    }

    private PartDefinition definition(String partId) {
        PartDefinition def = partDefinitions.get(partId);
        if (def == null) {
            throw new IllegalArgumentException("Unknown partId in catalog: " + partId);
        }
        return def;
    }

    public static void validateTasks(String partId, List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            throw new IllegalArgumentException("Part must have at least one task: " + partId);
        }
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).sequence() != i) {
                throw new IllegalArgumentException(
                        "Task sequences must be 0..n-1 for part " + partId);
            }
        }
    }
}
