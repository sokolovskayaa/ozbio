package scheduler.store.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import scheduler.model.schedule.Assignment;
import scheduler.model.machine.Machine;
import scheduler.model.machine.MachineGroup;
import scheduler.engine.policy.OrderPriorities;
import scheduler.model.order.Order;
import scheduler.model.order.Part;
import scheduler.model.order.Task;
import scheduler.store.json.ScheduleSnapshot;

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
        store.machineGroups.putAll(DemoFactoryCatalog.defaultMachineGroups());
        store.machines.addAll(DemoFactoryCatalog.defaultMachines(factoryStartedAt));
        return store;
    }

    public static ScheduleStore fromSnapshot(ScheduleSnapshot snapshot) {
        return ScheduleSnapshotMapper.fromSnapshot(snapshot);
    }

    public ScheduleSnapshot toSnapshot() {
        return ScheduleSnapshotMapper.toSnapshot(this);
    }

    void setFactoryStartedAt(Instant factoryStartedAt) {
        this.factoryStartedAt = factoryStartedAt;
    }

    void putMachineGroup(String groupId, MachineGroup group) {
        machineGroups.put(groupId, group);
    }

    void putAllMachineGroups(Map<String, MachineGroup> groups) {
        machineGroups.putAll(groups);
    }

    void addOrderRaw(Order order) {
        orders.add(order);
    }

    void putPartDefinitionRaw(String partId, PartDefinition definition) {
        partDefinitions.put(partId, definition);
    }

    boolean machineGroupsEmpty() {
        return machineGroups.isEmpty();
    }

    boolean machinesEmpty() {
        return machines.isEmpty();
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

    void sortOrders() {
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
