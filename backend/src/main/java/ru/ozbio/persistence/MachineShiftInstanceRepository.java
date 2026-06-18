package ru.ozbio.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.ozbio.service.model.MachineShiftClosableSummary;
import ru.ozbio.service.model.MachineShiftCloseTarget;

@Repository
public class MachineShiftInstanceRepository {

    private static final String SELECT_CLOSABLE =
            """
            SELECT ms.id,
                   ms.machine_id,
                   ms.shift_type_id,
                   ms.work_date,
                   ms.window_start,
                   ms.window_end,
                   ms.status,
                   st.day_of_week,
                   st.start_time,
                   st.end_time
            FROM machine_shift ms
            JOIN shift_type st ON st.id = ms.shift_type_id
            WHERE ms.status NOT IN ('CLOSED', 'CLOSED_EMPTY')
              AND ms.window_start <= ?
            ORDER BY ms.window_end ASC
            """;

    private static final String SELECT_BY_ID_FOR_UPDATE =
            """
            SELECT id, machine_id, window_start, status
            FROM machine_shift
            WHERE id = ?
            FOR UPDATE
            """;

    private static final String UPDATE_CLOSED =
            """
            UPDATE machine_shift
            SET status = ?, closed_at = ?
            WHERE id = ?
            """;

    private final JdbcTemplate jdbc;

    public MachineShiftInstanceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<MachineShiftClosableSummary> findClosable(Instant now) {
        return jdbc.query(SELECT_CLOSABLE, (rs, rowNum) -> mapClosable(rs), now);
    }

    public Optional<MachineShiftCloseTarget> findByIdForUpdate(long id) {
        return jdbc.query(SELECT_BY_ID_FOR_UPDATE, (rs, rowNum) -> mapCloseTarget(rs), id).stream()
                .findFirst();
    }

    public void updateClosed(long id, String status, Instant closedAt) {
        jdbc.update(UPDATE_CLOSED, status, closedAt, id);
    }

    private static MachineShiftClosableSummary mapClosable(ResultSet rs) throws SQLException {
        return new MachineShiftClosableSummary(
                rs.getLong("id"),
                rs.getLong("machine_id"),
                rs.getLong("shift_type_id"),
                rs.getDate("work_date").toLocalDate(),
                rs.getObject("window_start", Instant.class),
                rs.getObject("window_end", Instant.class),
                rs.getString("status"),
                rs.getInt("day_of_week"),
                rs.getObject("start_time", java.time.LocalTime.class),
                rs.getObject("end_time", java.time.LocalTime.class));
    }

    private static MachineShiftCloseTarget mapCloseTarget(ResultSet rs) throws SQLException {
        return new MachineShiftCloseTarget(
                rs.getLong("id"),
                rs.getLong("machine_id"),
                rs.getObject("window_start", Instant.class),
                rs.getString("status"));
    }
}
