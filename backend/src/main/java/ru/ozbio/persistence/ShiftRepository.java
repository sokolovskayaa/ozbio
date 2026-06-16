package ru.ozbio.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.ozbio.service.model.CreateShiftTypeCommand;
import ru.ozbio.service.model.ShiftTypeSummary;

@Repository
public class ShiftRepository {

    private static final String INSERT_SHIFT_TYPE =
            """
            INSERT INTO shift_type (day_of_week, start_time, end_time)
            VALUES (?, ?, ?)
            RETURNING id, day_of_week, start_time, end_time
            """;

    private final JdbcTemplate jdbc;

    public ShiftRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ShiftTypeSummary insert(CreateShiftTypeCommand command) {
        return jdbc.queryForObject(
                INSERT_SHIFT_TYPE,
                (rs, rowNum) -> mapShiftType(rs),
                command.dayOfWeek(),
                command.startTime(),
                command.endTime());
    }

    public boolean deleteById(long id) {
        return jdbc.update("DELETE FROM shift_type WHERE id = ?", id) > 0;
    }

    private static ShiftTypeSummary mapShiftType(ResultSet rs) throws SQLException {
        return new ShiftTypeSummary(
                rs.getLong("id"),
                rs.getInt("day_of_week"),
                rs.getObject("start_time", LocalTime.class),
                rs.getObject("end_time", LocalTime.class));
    }
}
