package scheduler.store.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import scheduler.model.machine.Capability;
import scheduler.model.machine.MachineStatus;
import scheduler.model.order.Task;
import scheduler.model.schedule.Assignment;
import scheduler.model.schedule.AssignmentStatus;

final class JdbcSupport {
    private JdbcSupport() {}

    static Instant readInstant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    static Task mapTask(ResultSet rs) throws SQLException {
        return new Task(
                rs.getString("task_id"),
                rs.getInt("sequence"),
                Duration.ofSeconds(rs.getLong("duration_seconds")),
                Capability.valueOf(rs.getString("required_capability")));
    }

    static Assignment mapAssignment(ResultSet rs) throws SQLException {
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

    static boolean factoryStateEmpty(org.springframework.jdbc.core.JdbcTemplate jdbc) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM factory_state", Integer.class);
        return count == null || count == 0;
    }

    static void requireFactoryState(org.springframework.jdbc.core.JdbcTemplate jdbc) {
        if (factoryStateEmpty(jdbc)) {
            throw new IllegalStateException(
                    "Database is empty: factory_state has no rows. "
                            + "Run migrations and seed catalog: profile demo, or ./scripts/seed-demo-catalog.sh");
        }
    }
}
