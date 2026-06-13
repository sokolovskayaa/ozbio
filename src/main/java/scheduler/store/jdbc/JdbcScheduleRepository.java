package scheduler.store.jdbc;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import scheduler.model.machine.Capability;
import scheduler.model.machine.Machine;
import scheduler.model.machine.MachineStatus;
import scheduler.model.order.Order;
import scheduler.model.order.Part;
import scheduler.model.order.Task;
import scheduler.model.schedule.Assignment;
import scheduler.model.schedule.AssignmentStatus;
import scheduler.store.ScheduleRepository;
import scheduler.store.core.ScheduleSnapshotMapper;
import scheduler.store.core.ScheduleStore;
import scheduler.store.json.ScheduleSnapshot;
import scheduler.store.snapshot.MachineGroupSnapshot;
import scheduler.store.snapshot.MachineSnapshot;
import scheduler.store.snapshot.PartDefinitionSnapshot;

@Repository
public class JdbcScheduleRepository implements ScheduleRepository {

    private final JdbcTemplate jdbc;

    public JdbcScheduleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ScheduleStore loadState() throws IOException {
        if (factoryStateEmpty()) {
            throw new IllegalStateException(
                    "Database is empty: factory_state has no rows. "
                            + "Run migrations and seed catalog: profile demo, or ./scripts/seed-demo-catalog.sh");
        }
        return load();
    }

    @Override
    public Instant factoryStartedAt() throws IOException {
        if (factoryStateEmpty()) {
            throw new IllegalStateException(
                    "Database is empty: factory_state has no rows. "
                            + "Run migrations and seed catalog: profile demo, or ./scripts/seed-demo-catalog.sh");
        }
        return jdbc.queryForObject(
                "SELECT factory_started_at FROM factory_state WHERE id = 1",
                (rs, rowNum) -> readInstant(rs, "factory_started_at"));
    }

    private boolean factoryStateEmpty() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM factory_state", Integer.class);
        return count == null || count == 0;
    }

    private ScheduleStore load() {
        ScheduleSnapshot snapshot = new ScheduleSnapshot();
        snapshot.factoryStartedAt = jdbc.queryForObject(
                "SELECT factory_started_at FROM factory_state WHERE id = 1",
                (rs, rowNum) -> readInstant(rs, "factory_started_at"));

        snapshot.machineGroups = jdbc.query(
                "SELECT group_id, name, setup_minutes FROM machine_group ORDER BY group_id",
                (rs, rowNum) -> {
                    MachineGroupSnapshot dto = new MachineGroupSnapshot();
                    dto.groupId = rs.getString("group_id");
                    dto.name = rs.getString("name");
                    dto.setupMinutes = rs.getInt("setup_minutes");
                    return dto;
                });

        Map<String, Set<Capability>> capabilitiesByMachine = new HashMap<>();
        jdbc.query(
                "SELECT machine_id, capability FROM machine_capability ORDER BY machine_id, capability",
                rs -> {
                    capabilitiesByMachine
                            .computeIfAbsent(rs.getString("machine_id"), k -> new java.util.LinkedHashSet<>())
                            .add(Capability.valueOf(rs.getString("capability")));
                });

        snapshot.machines = jdbc.query(
                "SELECT machine_id, group_id, available_at, status FROM machine ORDER BY machine_id",
                (rs, rowNum) -> new MachineSnapshot(
                        rs.getString("machine_id"),
                        rs.getString("group_id"),
                        capabilitiesByMachine.getOrDefault(rs.getString("machine_id"), Set.of()),
                        readInstant(rs, "available_at"),
                        MachineStatus.valueOf(rs.getString("status"))));

        snapshot.partDefinitions = new LinkedHashMap<>();
        Map<String, Integer> partPriorities = new HashMap<>();
        jdbc.query("SELECT part_id, priority FROM part_definition ORDER BY part_id", rs -> {
            partPriorities.put(rs.getString("part_id"), rs.getInt("priority"));
        });
        Map<String, List<Task>> tasksByPart = new TreeMap<>();
        jdbc.query(
                "SELECT part_id, task_id, sequence, duration_seconds, required_capability "
                        + "FROM part_task ORDER BY part_id, sequence",
                rs -> {
                    tasksByPart
                            .computeIfAbsent(rs.getString("part_id"), k -> new ArrayList<>())
                            .add(new Task(
                                    rs.getString("task_id"),
                                    rs.getInt("sequence"),
                                    Duration.ofSeconds(rs.getLong("duration_seconds")),
                                    Capability.valueOf(rs.getString("required_capability"))));
                });
        for (Map.Entry<String, Integer> entry : partPriorities.entrySet()) {
            PartDefinitionSnapshot dto = new PartDefinitionSnapshot();
            dto.priority = entry.getValue();
            dto.tasks = tasksByPart.getOrDefault(entry.getKey(), List.of());
            snapshot.partDefinitions.put(entry.getKey(), dto);
        }

        snapshot.orders = loadOrders();
        snapshot.assignments = loadAssignments();
        return ScheduleSnapshotMapper.fromSnapshot(snapshot);
    }

    private List<Order> loadOrders() {
        List<Order> orders = jdbc.query(
                "SELECT order_id, created_at, priority FROM schedule_order ORDER BY created_at, order_id",
                (rs, rowNum) -> new Order(
                        rs.getString("order_id"),
                        readInstant(rs, "created_at"),
                        List.of(),
                        rs.getInt("priority")));

        Map<String, List<Part>> partsByOrder = new HashMap<>();
        jdbc.query(
                "SELECT order_id, part_id, quantity FROM order_part ORDER BY order_id, part_id",
                rs -> {
                    String orderId = rs.getString("order_id");
                    String partId = rs.getString("part_id");
                    int quantity = rs.getInt("quantity");
                    List<Task> tasks = loadOrderPartTasks(orderId, partId);
                    partsByOrder.computeIfAbsent(orderId, k -> new ArrayList<>()).add(new Part(partId, quantity, tasks));
                });

        return orders.stream()
                .map(o -> new Order(o.orderId(), o.createdAt(), partsByOrder.getOrDefault(o.orderId(), List.of()), o.priority()))
                .toList();
    }

    private List<Task> loadOrderPartTasks(String orderId, String partId) {
        return jdbc.query(
                "SELECT task_id, sequence, duration_seconds, required_capability "
                        + "FROM order_part_task WHERE order_id = ? AND part_id = ? ORDER BY sequence",
                (rs, rowNum) -> new Task(
                        rs.getString("task_id"),
                        rs.getInt("sequence"),
                        Duration.ofSeconds(rs.getLong("duration_seconds")),
                        Capability.valueOf(rs.getString("required_capability"))),
                orderId,
                partId);
    }

    private List<Assignment> loadAssignments() {
        return jdbc.query(
                """
                SELECT assignment_id, order_id, part_id, unit_index, task_id, sequence,
                       machine_id, planned_start, planned_end, status, actual_start, actual_end
                FROM assignment
                ORDER BY planned_start, assignment_id
                """,
                this::mapAssignment);
    }

    private Assignment mapAssignment(ResultSet rs, int rowNum) throws SQLException {
        return new Assignment(
                rs.getString("assignment_id"),
                rs.getString("order_id"),
                rs.getString("part_id"),
                rs.getInt("unit_index"),
                rs.getString("task_id"),
                rs.getInt("sequence"),
                rs.getString("machine_id"),
                readInstant(rs, "planned_start"),
                readInstant(rs, "planned_end"),
                AssignmentStatus.valueOf(rs.getString("status")),
                readInstant(rs, "actual_start"),
                readInstant(rs, "actual_end"));
    }

    private static Instant readInstant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    @Override
    @Transactional
    public void persistOrderScheduling(ScheduleStore store, String orderId) throws IOException {
        Order order = store.findOrder(orderId)
                .orElseThrow(() -> new IllegalStateException("Order not found in working state: " + orderId));
        try {
            jdbc.update(
                    "INSERT INTO schedule_order (order_id, created_at, priority) VALUES (?, ?, ?)",
                    order.orderId(),
                    order.createdAt(),
                    order.priority());
            for (Part part : order.parts()) {
                jdbc.update(
                        "INSERT INTO order_part (order_id, part_id, quantity) VALUES (?, ?, ?)",
                        order.orderId(),
                        part.partId(),
                        part.quantity());
                for (Task task : part.tasks()) {
                    jdbc.update(
                            "INSERT INTO order_part_task (order_id, part_id, task_id, sequence, duration_seconds, required_capability) "
                                    + "VALUES (?, ?, ?, ?, ?, ?)",
                            order.orderId(),
                            part.partId(),
                            task.taskId(),
                            task.sequence(),
                            task.duration().getSeconds(),
                            task.requiredCapability().name());
                }
            }

            for (Assignment assignment : store.assignments()) {
                if (!assignment.orderId().equals(orderId)) {
                    continue;
                }
                jdbc.update(
                        """
                        INSERT INTO assignment (
                            assignment_id, order_id, part_id, unit_index, task_id, sequence,
                            machine_id, planned_start, planned_end, status, actual_start, actual_end
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        assignment.assignmentId(),
                        assignment.orderId(),
                        assignment.partId(),
                        assignment.unitIndex(),
                        assignment.taskId(),
                        assignment.sequence(),
                        assignment.machineId(),
                        assignment.plannedStart(),
                        assignment.plannedEnd(),
                        assignment.status().name(),
                        assignment.actualStart(),
                        assignment.actualEnd());
            }

            for (Machine machine : store.machines()) {
                jdbc.update(
                        "UPDATE machine SET available_at = ?, status = ? WHERE machine_id = ?",
                        machine.availableAt(),
                        machine.status().name(),
                        machine.machineId());
            }
        } catch (RuntimeException e) {
            throw new IOException("Failed to persist order scheduling to database", e);
        }
    }
}
