package ru.ozbio.persistence;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.ozbio.service.model.MachineShiftKey;

@Repository
public class MachineShiftCalendarRepository {

    private static final String UPSERT_EXPECTED =
            """
            INSERT INTO machine_shift (machine_id, shift_type_id, work_date, window_start, window_end, status)
            VALUES (?, ?, ?, ?, ?, 'EXPECTED')
            ON CONFLICT (machine_id, shift_type_id, work_date)
            DO UPDATE SET
                window_start = EXCLUDED.window_start,
                window_end = EXCLUDED.window_end
            WHERE machine_shift.status = 'EXPECTED'
            """;

    private static final String SELECT_EXPECTED_KEYS_IN_RANGE =
            """
            SELECT machine_id, shift_type_id, work_date
            FROM machine_shift
            WHERE status = 'EXPECTED'
              AND work_date >= ?
              AND work_date <= ?
            """;

    private static final String DELETE_EXPECTED =
            """
            DELETE FROM machine_shift
            WHERE status = 'EXPECTED'
              AND machine_id = ?
              AND shift_type_id = ?
              AND work_date = ?
            """;

    private final JdbcTemplate jdbc;

    public MachineShiftCalendarRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsertExpected(MachineShiftKey key, Instant windowStart, Instant windowEnd) {
        jdbc.update(
                UPSERT_EXPECTED,
                key.machineId(),
                key.shiftTypeId(),
                Date.valueOf(key.workDate()),
                windowStart,
                windowEnd);
    }

    public Set<MachineShiftKey> findExpectedKeysInRange(LocalDate from, LocalDate to) {
        List<MachineShiftKey> keys =
                jdbc.query(
                        SELECT_EXPECTED_KEYS_IN_RANGE,
                        (rs, rowNum) -> mapKey(rs),
                        Date.valueOf(from),
                        Date.valueOf(to));
        return new HashSet<>(keys);
    }

    public int deleteStaleExpected(LocalDate from, LocalDate to, Set<MachineShiftKey> expectedKeys) {
        Set<MachineShiftKey> existing = findExpectedKeysInRange(from, to);
        int deleted = 0;
        for (MachineShiftKey key : existing) {
            if (!expectedKeys.contains(key)) {
                deleted += deleteExpected(key);
            }
        }
        return deleted;
    }

    private int deleteExpected(MachineShiftKey key) {
        return jdbc.update(
                DELETE_EXPECTED,
                key.machineId(),
                key.shiftTypeId(),
                Date.valueOf(key.workDate()));
    }

    private static MachineShiftKey mapKey(ResultSet rs) throws SQLException {
        return new MachineShiftKey(
                rs.getLong("machine_id"),
                rs.getLong("shift_type_id"),
                rs.getDate("work_date").toLocalDate());
    }
}
