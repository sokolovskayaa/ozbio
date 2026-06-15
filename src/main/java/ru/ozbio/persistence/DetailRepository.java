package ru.ozbio.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.ozbio.service.model.CreateDetailCommand;
import ru.ozbio.service.model.DetailSummary;
import ru.ozbio.service.model.OperationLine;

@Repository
public class DetailRepository {

    private static final String INSERT_DETAIL =
            """
            INSERT INTO detail (name)
            VALUES (?)
            RETURNING id, name
            """;

    private static final String INSERT_OPERATION =
            """
            INSERT INTO operation (detail_id, duration, machine_type_id, step, setup_duration)
            VALUES (?, make_interval(secs => ?), ?, ?, make_interval(secs => ?))
            RETURNING id, step, machine_type_id
            """;

    private static final String SELECT_OPERATIONS =
            """
            SELECT id,
                   step,
                   machine_type_id,
                   EXTRACT(EPOCH FROM duration)::bigint AS duration_seconds,
                   EXTRACT(EPOCH FROM setup_duration)::bigint AS setup_duration_seconds
            FROM operation
            WHERE detail_id = ?
            ORDER BY step
            """;

    private final JdbcTemplate jdbc;

    public DetailRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public DetailSummary insert(CreateDetailCommand command) {
        DetailSummary detail =
                jdbc.queryForObject(
                        INSERT_DETAIL,
                        (rs, rowNum) -> new DetailSummary(rs.getLong("id"), rs.getString("name")),
                        command.name());

        for (CreateDetailCommand.Operation operation : command.operations()) {
            jdbc.update(
                    INSERT_OPERATION,
                    detail.id(),
                    operation.duration().toSeconds(),
                    operation.machineTypeId(),
                    operation.step(),
                    operation.setupDuration().toSeconds());
        }

        return detail;
    }

    public List<OperationLine> findOperationsByDetailId(long detailId) {
        return jdbc.query(
                SELECT_OPERATIONS,
                (rs, rowNum) -> mapOperation(rs),
                detailId);
    }

    public boolean existsById(long id) {
        Boolean exists =
                jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM detail WHERE id = ?)", Boolean.class, id);
        return Boolean.TRUE.equals(exists);
    }

    public boolean isReferenced(long detailId) {
        Boolean inOrders =
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM order_detail WHERE detail_id = ?)", Boolean.class, detailId);
        if (Boolean.TRUE.equals(inOrders)) {
            return true;
        }
        Boolean inTools =
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM tool_detail WHERE detail_id = ?)", Boolean.class, detailId);
        return Boolean.TRUE.equals(inTools);
    }

    public boolean machineTypeExists(long machineTypeId) {
        Boolean exists =
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM machine_type WHERE id = ?)", Boolean.class, machineTypeId);
        return Boolean.TRUE.equals(exists);
    }

    public boolean deleteById(long id) {
        jdbc.update("DELETE FROM operation WHERE detail_id = ?", id);
        return jdbc.update("DELETE FROM detail WHERE id = ?", id) > 0;
    }

    private static OperationLine mapOperation(ResultSet rs) throws SQLException {
        return new OperationLine(
                rs.getLong("id"),
                rs.getInt("step"),
                Duration.ofSeconds(rs.getLong("duration_seconds")),
                Duration.ofSeconds(rs.getLong("setup_duration_seconds")),
                rs.getLong("machine_type_id"));
    }
}
