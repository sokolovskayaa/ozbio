package scheduler.store.jdbc;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
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
import scheduler.store.PlanningRepository;
@Repository
public class JdbcPlanningRepository implements PlanningRepository {

    private final JdbcTemplate jdbc;

    public JdbcPlanningRepository(JdbcTemplate jdbc) {
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
    public boolean partExists(String partId) throws IOException {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM part_definition WHERE part_id = ?", Integer.class, partId);
            return count != null && count > 0;
        } catch (RuntimeException e) {
            throw new IOException("Failed to check part " + partId, e);
        }
    }

    @Override
    public int partPriority(String partId) throws IOException {
        try {
            return jdbc.queryForObject(
                    "SELECT priority FROM part_definition WHERE part_id = ?", Integer.class, partId);
        } catch (RuntimeException e) {
            throw new IOException("Failed to read priority for part " + partId, e);
        }
    }

    @Override
    public List<Task> partTasks(String partId) throws IOException {
        try {
            return jdbc.query(
                    "SELECT task_id, sequence, duration_seconds, required_capability "
                            + "FROM part_task WHERE part_id = ? ORDER BY sequence",
                    (rs, rowNum) -> JdbcSupport.mapTask(rs),
                    partId);
        } catch (RuntimeException e) {
            throw new IOException("Failed to read tasks for part " + partId, e);
        }
    }

    @Override
    public boolean hasOperationalMachineForCapability(Capability capability) throws IOException {
        try {
            Integer count = jdbc.queryForObject(
                    """
                    SELECT COUNT(*) FROM machine m
                    JOIN machine_capability mc ON mc.machine_id = m.machine_id
                    WHERE mc.capability = ? AND m.status IN ('IDLE', 'BUSY')
                    """,
                    Integer.class,
                    capability.name());
            return count != null && count > 0;
        } catch (RuntimeException e) {
            throw new IOException("Failed to check machines for capability " + capability, e);
        }
    }

    @Override
    public List<Machine> findOperationalMachines(Capability capability) throws IOException {
        try {
            Map<String, Set<Capability>> capabilitiesByMachine = new HashMap<>();
            jdbc.query(
                    "SELECT machine_id, capability FROM machine_capability ORDER BY machine_id, capability",
                    (RowCallbackHandler)
                            rs -> capabilitiesByMachine
                                    .computeIfAbsent(rs.getString("machine_id"), k -> new java.util.LinkedHashSet<>())
                                    .add(Capability.valueOf(rs.getString("capability"))));

            return jdbc.query(
                    """
                    SELECT m.machine_id, m.group_id, m.available_at, m.status
                    FROM machine m
                    JOIN machine_capability mc ON mc.machine_id = m.machine_id
                    WHERE mc.capability = ? AND m.status IN ('IDLE', 'BUSY')
                    ORDER BY m.available_at, m.machine_id
                    """,
                    (rs, rowNum) -> new Machine(
                            rs.getString("machine_id"),
                            rs.getString("group_id"),
                            capabilitiesByMachine.getOrDefault(rs.getString("machine_id"), Set.of()),
                            JdbcSupport.readInstant(rs, "available_at"),
                            MachineStatus.valueOf(rs.getString("status"))),
                    capability.name());
        } catch (RuntimeException e) {
            throw new IOException("Failed to find machines for capability " + capability, e);
        }
    }

    @Override
    public Machine findMachine(String machineId) throws IOException {
        try {
            Set<Capability> capabilities = new java.util.LinkedHashSet<>();
            jdbc.query(
                    "SELECT capability FROM machine_capability WHERE machine_id = ? ORDER BY capability",
                    (RowCallbackHandler)
                            rs -> capabilities.add(Capability.valueOf(rs.getString("capability"))),
                    machineId);

            return jdbc.queryForObject(
                    "SELECT machine_id, group_id, available_at, status FROM machine WHERE machine_id = ?",
                    (rs, rowNum) -> new Machine(
                            rs.getString("machine_id"),
                            rs.getString("group_id"),
                            capabilities,
                            JdbcSupport.readInstant(rs, "available_at"),
                            MachineStatus.valueOf(rs.getString("status"))),
                    machineId);
        } catch (RuntimeException e) {
            throw new IOException("Failed to find machine " + machineId, e);
        }
    }

    @Override
    public Optional<MachineGroup> groupForMachine(String machineId) throws IOException {
        try {
            return jdbc.query(
                    """
                    SELECT g.group_id, g.name, g.setup_minutes
                    FROM machine_group g
                    JOIN machine m ON m.group_id = g.group_id
                    WHERE m.machine_id = ?
                    """,
                    rs -> {
                        if (!rs.next()) {
                            return Optional.empty();
                        }
                        return Optional.of(new MachineGroup(
                                rs.getString("group_id"),
                                rs.getString("name"),
                                java.time.Duration.ofMinutes(rs.getInt("setup_minutes"))));
                    },
                    machineId);
        } catch (RuntimeException e) {
            throw new IOException("Failed to find group for machine " + machineId, e);
        }
    }

    @Override
    public boolean orderExists(String orderId) throws IOException {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM schedule_order WHERE order_id = ?", Integer.class, orderId);
            return count != null && count > 0;
        } catch (RuntimeException e) {
            throw new IOException("Failed to check order " + orderId, e);
        }
    }

    @Override
    public List<String> listOrderIds() throws IOException {
        try {
            return jdbc.queryForList("SELECT order_id FROM schedule_order ORDER BY created_at, order_id", String.class);
        } catch (RuntimeException e) {
            throw new IOException("Failed to list order ids", e);
        }
    }

    @Override
    public List<Order> ordersWithPriorityAbove(int priority) throws IOException {
        try {
            List<Order> orders = jdbc.query(
                    "SELECT order_id, created_at, priority FROM schedule_order WHERE priority > ? ORDER BY priority, created_at",
                    (rs, rowNum) -> new Order(
                            rs.getString("order_id"),
                            JdbcSupport.readInstant(rs, "created_at"),
                            List.of(),
                            rs.getInt("priority")),
                    priority);
            return orders;
        } catch (RuntimeException e) {
            throw new IOException("Failed to list orders with priority above " + priority, e);
        }
    }

    @Override
    public List<Assignment> assignmentsForOrder(String orderId) throws IOException {
        try {
            return jdbc.query(
                    """
                    SELECT assignment_id, order_id, part_id, unit_index, task_id, sequence,
                           machine_id, planned_start, planned_end, status, actual_start, actual_end
                    FROM assignment
                    WHERE order_id = ?
                    ORDER BY planned_start, assignment_id
                    """,
                    (rs, rowNum) -> JdbcSupport.mapAssignment(rs),
                    orderId);
        } catch (RuntimeException e) {
            throw new IOException("Failed to load assignments for order " + orderId, e);
        }
    }

    @Override
    public Optional<Instant> orderReadyAt(String orderId) throws IOException {
        try {
            Instant ready = jdbc.queryForObject(
                    """
                    SELECT MAX(
                        CASE WHEN status = 'COMPLETED' AND actual_end IS NOT NULL THEN actual_end ELSE planned_end END
                    )
                    FROM assignment
                    WHERE order_id = ? AND status != 'CANCELLED'
                    """,
                    Instant.class,
                    orderId);
            return Optional.ofNullable(ready);
        } catch (RuntimeException e) {
            throw new IOException("Failed to read readyAt for order " + orderId, e);
        }
    }

    @Override
    public Instant machineAvailableFrom(String machineId, Instant now) throws IOException {
        try {
            Instant factoryStartedAt = factoryStartedAt();
            Instant baseline = factoryStartedAt.isBefore(now) ? now : factoryStartedAt;
            Machine machine = findMachine(machineId);
            Instant latest = machine.availableAt().isAfter(baseline) ? machine.availableAt() : baseline;

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
        } catch (RuntimeException e) {
            throw new IOException("Failed to compute machine availability for " + machineId, e);
        }
    }

    @Override
    public Optional<Assignment> lastWorkOnMachine(String machineId) throws IOException {
        try {
            List<Assignment> rows = jdbc.query(
                    """
                    SELECT assignment_id, order_id, part_id, unit_index, task_id, sequence,
                           machine_id, planned_start, planned_end, status, actual_start, actual_end
                    FROM assignment
                    WHERE machine_id = ? AND status != 'CANCELLED'
                    ORDER BY
                        CASE WHEN status = 'COMPLETED' AND actual_end IS NOT NULL THEN actual_end ELSE planned_end END DESC,
                        assignment_id DESC
                    LIMIT 20
                    """,
                    (rs, rowNum) -> JdbcSupport.mapAssignment(rs),
                    machineId);
            return AssignmentFilters.work(rows).stream()
                    .filter(a -> a.isCompleted() || a.isPlanned())
                    .findFirst();
        } catch (RuntimeException e) {
            throw new IOException("Failed to read last work on machine " + machineId, e);
        }
    }

    @Override
    public void syncOperationalMachines(Instant now) throws IOException {
        try {
            List<String> machines = jdbc.queryForList(
                    "SELECT machine_id FROM machine WHERE status IN ('IDLE', 'BUSY') ORDER BY machine_id",
                    String.class);
            for (String machineId : machines) {
                Instant effective = machineAvailableFrom(machineId, now);
                MachineStatus status = effective.isAfter(now) ? MachineStatus.BUSY : MachineStatus.IDLE;
                jdbc.update(
                        "UPDATE machine SET available_at = ?, status = ? WHERE machine_id = ?",
                        effective,
                        status.name(),
                        machineId);
            }
        } catch (RuntimeException e) {
            throw new IOException("Failed to sync machine states", e);
        }
    }

    @Override
    @Transactional
    public void insertOrder(Order order) throws IOException {
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
        } catch (RuntimeException e) {
            throw new IOException("Failed to insert order " + order.orderId(), e);
        }
    }

    @Override
    @Transactional
    public void insertAssignment(Assignment assignment) throws IOException {
        try {
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
        } catch (RuntimeException e) {
            throw new IOException("Failed to insert assignment " + assignment.assignmentId(), e);
        }
    }

    @Override
    @Transactional
    public void updateMachineAvailableAt(String machineId, Instant availableAt) throws IOException {
        try {
            jdbc.update("UPDATE machine SET available_at = ? WHERE machine_id = ?", availableAt, machineId);
        } catch (RuntimeException e) {
            throw new IOException("Failed to update machine " + machineId, e);
        }
    }
}
