package ru.ozbio.persistence;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.ozbio.api.dto.ShiftCompletionRequest;

@Repository
public class ShiftCompletionRepository {

    private static final String INSERT =
            """
            INSERT INTO shift_completion (machine_shift_id, operation_id, count)
            VALUES (?, ?, ?)
            """;

    private final JdbcTemplate jdbc;

    public ShiftCompletionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertAll(long machineShiftId, List<ShiftCompletionRequest> completions) {
        for (ShiftCompletionRequest completion : completions) {
            jdbc.update(
                    INSERT,
                    machineShiftId,
                    completion.operationId(),
                    completion.count());
        }
    }
}
