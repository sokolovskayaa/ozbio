package scheduler.store;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import scheduler.model.Assignment;
import scheduler.model.Capability;
import scheduler.model.Machine;
import scheduler.model.MachineGroup;
import scheduler.model.MachineGroupDefaults;
import scheduler.model.MachineStatus;
import scheduler.engine.OrderPriorities;
import scheduler.model.Order;
import scheduler.model.Part;
import scheduler.model.Task;

public class ScheduleStore {
    private Instant factoryStartedAt;
    private final List<Order> orders = new ArrayList<>();
    private final List<Assignment> assignments = new ArrayList<>();
    private final List<Machine> machines = new ArrayList<>();
    private final Map<String, MachineGroup> machineGroups = new LinkedHashMap<>();
    private final Map<String, PartDefinition> partDefinitions = new LinkedHashMap<>();

    public static ScheduleStore empty(Instant factoryStartedAt) {
        ScheduleStore store = new ScheduleStore();
        store.factoryStartedAt = factoryStartedAt;
        store.machineGroups.putAll(defaultMachineGroups());
        store.machines.addAll(defaultMachines(factoryStartedAt));
        return store;
    }

    public static ScheduleStore fromSnapshot(ScheduleSnapshot snapshot) {
        ScheduleStore store = new ScheduleStore();
        store.factoryStartedAt = snapshot.factoryStartedAt;
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
        return snapshot;
    }

    public static Map<String, MachineGroup> defaultMachineGroups() {
        Map<String, MachineGroup> groups = new LinkedHashMap<>();
        groups.put(
                "cnc",
                new MachineGroup(
                        "cnc",
                        "ЧПУ (фрезерный и токарный)",
                        List.of(),
                        MachineGroupDefaults.setupDuration("cnc")));
        groups.put(
                "heavy",
                new MachineGroup(
                        "heavy",
                        "Тяжёлое оборудование (расточка, шлифование)",
                        List.of(),
                        MachineGroupDefaults.setupDuration("heavy")));
        groups.put(
                "finish",
                new MachineGroup(
                        "finish",
                        "Сварка и сборка",
                        List.of(),
                        MachineGroupDefaults.setupDuration("finish")));
        return groups;
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

    public Instant factoryStartedAt() {
        return factoryStartedAt;
    }

    public List<Order> orders() {
        return List.copyOf(orders);
    }

    public List<Assignment> assignments() {
        return List.copyOf(assignments);
    }

    public List<Machine> machines() {
        return List.copyOf(machines);
    }

    public void updateMachineAvailability(String machineId, Instant availableAt) {
        machines.stream()
                .filter(m -> m.machineId().equals(machineId))
                .findFirst()
                .ifPresent(m -> {
                    if (availableAt.isAfter(m.availableAt())) {
                        m.setAvailableAt(availableAt);
                    }
                });
    }

    public void addMachine(Machine machine) {
        machines.add(machine);
    }

    public SchedulingSnapshot captureSchedulingState() {
        Map<String, Instant> machineAvailableAt = new LinkedHashMap<>();
        for (Machine m : machines) {
            machineAvailableAt.put(m.machineId(), m.availableAt());
        }
        return new SchedulingSnapshot(List.copyOf(orders), List.copyOf(assignments), machineAvailableAt);
    }

    public void rollbackTo(SchedulingSnapshot snapshot) {
        orders.clear();
        orders.addAll(snapshot.orders());
        assignments.clear();
        assignments.addAll(snapshot.assignments());
        for (Machine m : machines) {
            Instant at = snapshot.machineAvailableAt().get(m.machineId());
            if (at != null) {
                m.setAvailableAt(at);
            }
        }
        sortOrders();
    }

    public record SchedulingSnapshot(
            List<Order> orders, List<Assignment> assignments, Map<String, Instant> machineAvailableAt) {}

    public void addOrder(Order order) {
        orders.add(order);
        sortOrders();
    }

    public void addAssignment(Assignment assignment) {
        assignments.add(assignment);
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

    public Part createPart(String partId, int quantity) {
        PartDefinition def = definition(partId);
        return new Part(partId, quantity, def.tasks());
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
