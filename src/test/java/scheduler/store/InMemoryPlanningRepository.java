package scheduler.store;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import scheduler.engine.metrics.AssignmentFilters;
import scheduler.model.machine.Capability;
import scheduler.model.machine.Machine;
import scheduler.model.machine.MachineGroup;
import scheduler.model.machine.MachineStatus;
import scheduler.model.order.Order;
import scheduler.model.order.Part;
import scheduler.model.order.Task;
import scheduler.model.schedule.Assignment;
import scheduler.model.schedule.AssignmentStatus;
import scheduler.store.core.DemoFactoryCatalog;
import scheduler.store.core.PartDefinition;

/** In-memory store for unit tests — mirrors JDBC repository behaviour. */
public class InMemoryPlanningRepository implements PlanningRepository, ScheduleQueryRepository {

    private Instant factoryStartedAt;
    private final Map<String, MachineGroup> machineGroups = new LinkedHashMap<>();
    private final Map<String, Machine> machines = new LinkedHashMap<>();
    private final Map<String, PartDefinition> catalog = new LinkedHashMap<>();
    private final Map<String, Order> orders = new LinkedHashMap<>();
    private final List<Assignment> assignments = new ArrayList<>();

    public InMemoryPlanningRepository(Instant factoryStartedAt) {
        this.factoryStartedAt = factoryStartedAt;
        machineGroups.putAll(DemoFactoryCatalog.defaultMachineGroups());
        for (Machine machine : DemoFactoryCatalog.defaultMachines(factoryStartedAt)) {
            machines.put(machine.machineId(), machine);
        }
    }

    public void putPartDefinition(String partId, PartDefinition definition) {
        catalog.put(partId, definition);
    }

    public void addOrder(Order order) {
        orders.put(order.orderId(), order);
    }

    public void addAssignment(Assignment assignment) {
        assignments.add(assignment);
    }

    public Machine machine(String machineId) {
        return machines.get(machineId);
    }

    @Override
    public Instant factoryStartedAt() throws IOException {
        return factoryStartedAt;
    }

    @Override
    public ScheduleData loadScheduleData() throws IOException {
        syncOperationalMachines(factoryStartedAt);
        List<Order> ordersWithAssignments = orders.values().stream()
                .filter(o -> assignments.stream().anyMatch(a -> a.orderId().equals(o.orderId())))
                .sorted(Comparator.comparing(Order::createdAt).thenComparing(Order::orderId))
                .toList();
        return new ScheduleData(
                factoryStartedAt,
                Map.copyOf(catalog),
                Map.copyOf(machineGroups),
                machines.values().stream().sorted(Comparator.comparing(Machine::machineId)).toList(),
                ordersWithAssignments,
                List.copyOf(assignments));
    }

    @Override
    public boolean partExists(String partId) throws IOException {
        return catalog.containsKey(partId);
    }

    @Override
    public int partPriority(String partId) throws IOException {
        PartDefinition def = catalog.get(partId);
        if (def == null) {
            throw new IllegalArgumentException("Unknown partId in catalog: " + partId);
        }
        return def.priority();
    }

    @Override
    public List<Task> partTasks(String partId) throws IOException {
        PartDefinition def = catalog.get(partId);
        if (def == null) {
            throw new IllegalArgumentException("Unknown partId in catalog: " + partId);
        }
        return def.tasks();
    }

    @Override
    public boolean hasOperationalMachineForCapability(Capability capability) throws IOException {
        return machines.values().stream()
                .filter(Machine::isOperational)
                .anyMatch(m -> m.canPerform(capability));
    }

    @Override
    public List<Machine> findOperationalMachines(Capability capability) throws IOException {
        return machines.values().stream()
                .filter(Machine::isOperational)
                .filter(m -> m.canPerform(capability))
                .sorted(Comparator.comparing(Machine::availableAt))
                .toList();
    }

    @Override
    public Machine findMachine(String machineId) throws IOException {
        Machine machine = machines.get(machineId);
        if (machine == null) {
            throw new IllegalArgumentException("Unknown machine: " + machineId);
        }
        return machine;
    }

    @Override
    public Optional<MachineGroup> groupForMachine(String machineId) throws IOException {
        Machine machine = findMachine(machineId);
        return Optional.ofNullable(machineGroups.get(machine.groupId()));
    }

    @Override
    public boolean orderExists(String orderId) throws IOException {
        return orders.containsKey(orderId);
    }

    @Override
    public List<String> listOrderIds() throws IOException {
        return orders.values().stream()
                .sorted(Comparator.comparing(Order::createdAt).thenComparing(Order::orderId))
                .map(Order::orderId)
                .toList();
    }

    @Override
    public List<Order> ordersWithPriorityAbove(int priority) throws IOException {
        return orders.values().stream()
                .filter(o -> o.priority() > priority)
                .sorted(Comparator.comparing(Order::priority).thenComparing(Order::createdAt))
                .toList();
    }

    @Override
    public List<Assignment> assignmentsForOrder(String orderId) throws IOException {
        return assignments.stream()
                .filter(a -> a.orderId().equals(orderId))
                .sorted(Comparator.comparing(Assignment::plannedStart).thenComparing(Assignment::assignmentId))
                .toList();
    }

    @Override
    public Optional<Instant> orderReadyAt(String orderId) throws IOException {
        return AssignmentFilters.active(assignments).stream()
                .filter(a -> a.orderId().equals(orderId))
                .map(Assignment::effectiveEnd)
                .max(Comparator.naturalOrder());
    }

    @Override
    public Instant machineAvailableFrom(String machineId, Instant now) throws IOException {
        Instant baseline = factoryStartedAt.isBefore(now) ? now : factoryStartedAt;
        Machine machine = findMachine(machineId);
        Instant latest = machine.availableAt().isAfter(baseline) ? machine.availableAt() : baseline;
        for (Assignment a : assignments) {
            if (!a.machineId().equals(machineId) || a.status() == AssignmentStatus.CANCELLED) {
                continue;
            }
            Instant end = a.effectiveEnd();
            if (end.isAfter(latest)) {
                latest = end;
            }
        }
        return latest;
    }

    @Override
    public Optional<Assignment> lastWorkOnMachine(String machineId) throws IOException {
        return AssignmentFilters.work(assignments).stream()
                .filter(a -> a.machineId().equals(machineId))
                .filter(a -> a.isCompleted() || a.isPlanned())
                .max(Comparator.comparing(Assignment::effectiveEnd));
    }

    @Override
    public void syncOperationalMachines(Instant now) throws IOException {
        for (Machine machine : machines.values()) {
            if (!machine.isOperational()) {
                continue;
            }
            Instant effective = machineAvailableFrom(machine.machineId(), now);
            machine.setAvailableAt(effective);
            machine.setStatus(machine.availableAt().isAfter(now) ? MachineStatus.BUSY : MachineStatus.IDLE);
        }
    }

    @Override
    public void insertOrder(Order order) throws IOException {
        if (orders.containsKey(order.orderId())) {
            throw new IllegalStateException("Order already exists: " + order.orderId());
        }
        orders.put(order.orderId(), order);
    }

    @Override
    public void insertAssignment(Assignment assignment) throws IOException {
        assignments.add(assignment);
    }

    @Override
    public void updateMachineAvailableAt(String machineId, Instant availableAt) throws IOException {
        Machine machine = findMachine(machineId);
        if (availableAt.isAfter(machine.availableAt())) {
            machine.setAvailableAt(availableAt);
        }
    }
}
