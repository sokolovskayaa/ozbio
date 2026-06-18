package ru.ozbio.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.List;

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
        jdbc.update("DELETE FROM machine_shift_type WHERE shift_type_id = ?", id);
        return jdbc.update("DELETE FROM shift_type WHERE id = ?", id) > 0;
    }

    public boolean existsById(long id) {
        Boolean exists =
                jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM shift_type WHERE id = ?)", Boolean.class, id);
        return Boolean.TRUE.equals(exists);
    }

    public List<ShiftTypeSummary> findAll() {
        return jdbc.query(
                "SELECT id, day_of_week, start_time, end_time FROM shift_type ORDER BY id",
                (rs, rowNum) -> mapShiftType(rs));
    }

    private static ShiftTypeSummary mapShiftType(ResultSet rs) throws SQLException {
        return new ShiftTypeSummary(
                rs.getLong("id"),
                rs.getInt("day_of_week"),
                rs.getObject("start_time", LocalTime.class),
                rs.getObject("end_time", LocalTime.class));
    }
}
