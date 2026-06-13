package scheduler.store.jdbc;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;
import scheduler.model.machine.Capability;
import scheduler.model.machine.Machine;
import scheduler.model.machine.MachineGroup;
import scheduler.model.machine.MachineStatus;
import scheduler.model.order.Order;
import scheduler.model.order.Part;
import scheduler.model.order.Task;
import scheduler.model.schedule.Assignment;
import scheduler.store.ScheduleData;
import scheduler.store.ScheduleQueryRepository;
import scheduler.store.core.PartDefinition;

@Repository
public class JdbcScheduleQueryRepository implements ScheduleQueryRepository {

    private final JdbcTemplate jdbc;

    public JdbcScheduleQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Instant factoryStartedAt() throws IOException {
        try {
            JdbcSupport.requireFactoryState(jdbc);
            return jdbc.queryForObject(
                    "SELECT factory_started_at FROM factory_state WHERE id = 1",
                    (rs, rowNum) -> JdbcSupport.readInstant(rs, "factory_started_at"));
        } catch (RuntimeException e) {
            throw new IOException("Failed to read factory_started_at", e);
        }
    }

    @Override
    public ScheduleData loadScheduleData() throws IOException {
        try {
            JdbcSupport.requireFactoryState(jdbc);
            Instant factoryStartedAt = factoryStartedAt();

            Map<String, MachineGroup> machineGroups = loadMachineGroups();
            List<Machine> machines = loadMachines();
            syncMachinesForView(machines, factoryStartedAt);

            Map<String, PartDefinition> catalog = loadCatalog();
            List<Assignment> assignments = loadAllAssignments();
            List<Order> orders = loadOrdersWithParts(assignments);

            return new ScheduleData(factoryStartedAt, catalog, machineGroups, machines, orders, assignments);
        } catch (RuntimeException e) {
            throw new IOException("Failed to load schedule data", e);
        }
    }

    private Map<String, MachineGroup> loadMachineGroups() {
        Map<String, MachineGroup> groups = new LinkedHashMap<>();
        jdbc.query(
                "SELECT group_id, name, setup_minutes FROM machine_group ORDER BY group_id",
                (RowCallbackHandler) rs -> groups.put(
                        rs.getString("group_id"),
                        new MachineGroup(
                                rs.getString("group_id"),
                                rs.getString("name"),
                                java.time.Duration.ofMinutes(rs.getInt("setup_minutes")))));
        return groups;
    }

    private List<Machine> loadMachines() {
        Map<String, Set<Capability>> capabilitiesByMachine = new HashMap<>();
        jdbc.query(
                "SELECT machine_id, capability FROM machine_capability ORDER BY machine_id, capability",
                (RowCallbackHandler)
                        rs -> capabilitiesByMachine
                                .computeIfAbsent(rs.getString("machine_id"), k -> new java.util.LinkedHashSet<>())
                                .add(Capability.valueOf(rs.getString("capability"))));

        return jdbc.query(
                "SELECT machine_id, group_id, available_at, status FROM machine ORDER BY machine_id",
                (rs, rowNum) -> new Machine(
                        rs.getString("machine_id"),
                        rs.getString("group_id"),
                        capabilitiesByMachine.getOrDefault(rs.getString("machine_id"), Set.of()),
                        JdbcSupport.readInstant(rs, "available_at"),
                        MachineStatus.valueOf(rs.getString("status"))));
    }

    private void syncMachinesForView(List<Machine> machines, Instant factoryStartedAt) {
        Instant now = factoryStartedAt;
        for (Machine machine : machines) {
            if (!machine.isOperational()) {
                continue;
            }
            Instant effective = machineAvailableFrom(machine.machineId(), factoryStartedAt, now);
            machine.setAvailableAt(effective);
            machine.setStatus(machine.availableAt().isAfter(now) ? MachineStatus.BUSY : MachineStatus.IDLE);
        }
    }

    private Instant machineAvailableFrom(String machineId, Instant factoryStartedAt, Instant now) {
        Instant baseline = factoryStartedAt.isBefore(now) ? now : factoryStartedAt;
        Instant machineAvailable = jdbc.queryForObject(
                "SELECT available_at FROM machine WHERE machine_id = ?",
                (rs, rowNum) -> JdbcSupport.readInstant(rs, "available_at"),
                machineId);
        Instant latest = machineAvailable.isAfter(baseline) ? machineAvailable : baseline;

        Instant maxEnd = jdbc.queryForObject(
                """
                SELECT COALESCE(MAX(
                    CASE WHEN status = 'COMPLETED' AND actual_end IS NOT NULL THEN actual_end ELSE planned_end END
                ), ?)
                FROM assignment
                WHERE machine_id = ? AND status != 'CANCELLED'
                """,
                Instant.class,
                factoryStartedAt,
                machineId);
        if (maxEnd != null && maxEnd.isAfter(latest)) {
            latest = maxEnd;
        }
        return latest;
    }

    private Map<String, PartDefinition> loadCatalog() {
        Map<String, Integer> priorities = new HashMap<>();
        jdbc.query(
                "SELECT part_id, priority FROM part_definition ORDER BY part_id",
                (RowCallbackHandler) rs -> priorities.put(rs.getString("part_id"), rs.getInt("priority")));

        Map<String, List<Task>> tasksByPart = new TreeMap<>();
        jdbc.query(
                "SELECT part_id, task_id, sequence, duration_seconds, required_capability "
                        + "FROM part_task ORDER BY part_id, sequence",
                (RowCallbackHandler)
                        rs -> tasksByPart
                                .computeIfAbsent(rs.getString("part_id"), k -> new ArrayList<>())
                                .add(JdbcSupport.mapTask(rs)));

        Map<String, PartDefinition> catalog = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : priorities.entrySet()) {
            catalog.put(entry.getKey(), new PartDefinition(entry.getValue(), tasksByPart.getOrDefault(entry.getKey(), List.of())));
        }
        return catalog;
    }

    private List<Order> loadOrdersWithParts(List<Assignment> assignments) {
        Set<String> orderIdsWithAssignments = new java.util.LinkedHashSet<>();
        for (Assignment a : assignments) {
            orderIdsWithAssignments.add(a.orderId());
        }
        if (orderIdsWithAssignments.isEmpty()) {
            return List.of();
        }

        List<Order> orders = jdbc.query(
                "SELECT order_id, created_at, priority FROM schedule_order ORDER BY created_at, order_id",
                (rs, rowNum) -> new Order(
                        rs.getString("order_id"),
                        JdbcSupport.readInstant(rs, "created_at"),
                        List.of(),
                        rs.getInt("priority")));

        Map<String, List<Part>> partsByOrder = new HashMap<>();
        jdbc.query(
                "SELECT order_id, part_id, quantity FROM order_part ORDER BY order_id, part_id",
                (RowCallbackHandler) rs -> {
                    String orderId = rs.getString("order_id");
                    String partId = rs.getString("part_id");
                    int quantity = rs.getInt("quantity");
                    List<Task> tasks = loadOrderPartTasks(orderId, partId);
                    partsByOrder.computeIfAbsent(orderId, k -> new ArrayList<>()).add(new Part(partId, quantity, tasks));
                });

        return orders.stream()
                .filter(o -> orderIdsWithAssignments.contains(o.orderId()))
                .map(o -> new Order(
                        o.orderId(),
                        o.createdAt(),
                        partsByOrder.getOrDefault(o.orderId(), List.of()),
                        o.priority()))
                .toList();
    }

    private List<Task> loadOrderPartTasks(String orderId, String partId) {
        return jdbc.query(
                "SELECT task_id, sequence, duration_seconds, required_capability "
                        + "FROM order_part_task WHERE order_id = ? AND part_id = ? ORDER BY sequence",
                (rs, rowNum) -> JdbcSupport.mapTask(rs),
                orderId,
                partId);
    }

    private List<Assignment> loadAllAssignments() {
        return jdbc.query(
                """
                SELECT assignment_id, order_id, part_id, unit_index, task_id, sequence,
                       machine_id, planned_start, planned_end, status, actual_start, actual_end
                FROM assignment
                ORDER BY planned_start, assignment_id
                """,
                (rs, rowNum) -> JdbcSupport.mapAssignment(rs));
    }
}
