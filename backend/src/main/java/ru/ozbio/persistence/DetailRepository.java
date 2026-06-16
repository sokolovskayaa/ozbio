package ru.ozbio.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.ozbio.persistence.jdbc.JdbcSupport;
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

    private static final String SELECT_OPERATIONS_BY_DETAIL_IDS_PREFIX =
            """
            SELECT detail_id,
                   id,
                   step,
                   machine_type_id,
                   EXTRACT(EPOCH FROM duration)::bigint AS duration_seconds,
                   EXTRACT(EPOCH FROM setup_duration)::bigint AS setup_duration_seconds
            FROM operation
            WHERE detail_id IN (
            """;

    private static final String SELECT_OPERATIONS_BY_IDS_PREFIX =
            """
            SELECT id,
                   step,
                   machine_type_id,
                   EXTRACT(EPOCH FROM duration)::bigint AS duration_seconds,
                   EXTRACT(EPOCH FROM setup_duration)::bigint AS setup_duration_seconds
            FROM operation
            WHERE id IN (
            """;

    private static final String SELECT_ALL_DETAILS =
            """
            SELECT id, name
            FROM detail
            ORDER BY id
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

    public List<DetailSummary> findAll() {
        return jdbc.query(
                SELECT_ALL_DETAILS,
                (rs, rowNum) -> new DetailSummary(rs.getLong("id"), rs.getString("name")));
    }

    public List<OperationLine> findOperationsByDetailId(long detailId) {
        return jdbc.query(
                SELECT_OPERATIONS,
                (rs, rowNum) -> mapOperation(rs),
                detailId);
    }

    public Map<Long, List<OperationLine>> findOperationsByDetailIds(Collection<Long> detailIds) {
        if (detailIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = List.copyOf(detailIds);
        String sql =
                SELECT_OPERATIONS_BY_DETAIL_IDS_PREFIX
                        + JdbcSupport.placeholders(ids.size())
                        + ") ORDER BY detail_id, step";

        Map<Long, List<OperationLine>> result = new HashMap<>();
        jdbc.query(
                sql,
                rs -> {
                    long detailId = rs.getLong("detail_id");
                    result.computeIfAbsent(detailId, ignored -> new ArrayList<>()).add(mapOperation(rs));
                },
                ids.toArray());

        return result;
    }

    public Map<Long, OperationLine> findOperationsByIds(Collection<Long> operationIds) {
        if (operationIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = List.copyOf(operationIds);
        String sql =
                SELECT_OPERATIONS_BY_IDS_PREFIX
                        + JdbcSupport.placeholders(ids.size())
                        + ")";

        Map<Long, OperationLine> result = new HashMap<>();
        jdbc.query(
                sql,
                rs -> {
                    OperationLine operation = mapOperation(rs);
                    result.put(operation.id(), operation);
                },
                ids.toArray());

        return result;
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
