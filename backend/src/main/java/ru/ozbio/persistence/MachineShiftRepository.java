package ru.ozbio.persistence;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.ozbio.persistence.jdbc.JdbcSupport;
import ru.ozbio.service.model.MachineShiftTypeLink;

@Repository
public class MachineShiftRepository {

    private static final String INSERT_LINK =
            """
            INSERT INTO machine_shift_type (machine_id, shift_type_id)
            VALUES (?, ?)
            ON CONFLICT DO NOTHING
            """;

    private static final String SELECT_MACHINE_IDS_BY_SHIFT_TYPE =
            """
            SELECT machine_id
            FROM machine_shift_type
            WHERE shift_type_id = ?
            ORDER BY machine_id
            """;

    private static final String SELECT_ALL_LINKS =
            """
            SELECT machine_id, shift_type_id
            FROM machine_shift_type
            ORDER BY machine_id, shift_type_id
            """;

    private final JdbcTemplate jdbc;

    public MachineShiftRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void linkMachines(long shiftTypeId, Collection<Long> machineIds) {
        for (long machineId : machineIds) {
            jdbc.update(INSERT_LINK, machineId, shiftTypeId);
        }
    }

    public void unlinkMachine(long shiftTypeId, long machineId) {
        jdbc.update(
                "DELETE FROM machine_shift_type WHERE shift_type_id = ? AND machine_id = ?",
                shiftTypeId,
                machineId);
    }

    public List<Long> findMachineIdsByShiftTypeId(long shiftTypeId) {
        return jdbc.queryForList(SELECT_MACHINE_IDS_BY_SHIFT_TYPE, Long.class, shiftTypeId);
    }

    public List<MachineShiftTypeLink> findAllLinks() {
        return jdbc.query(
                SELECT_ALL_LINKS,
                (rs, rowNum) ->
                        new MachineShiftTypeLink(rs.getLong("machine_id"), rs.getLong("shift_type_id")));
    }

    public boolean machineExists(long machineId) {
        Boolean exists =
                jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM machine WHERE id = ?)", Boolean.class, machineId);
        return Boolean.TRUE.equals(exists);
    }

    public Set<Long> findExistingMachineIds(Collection<Long> machineIds) {
        if (machineIds.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = List.copyOf(machineIds);
        String sql =
                "SELECT id FROM machine WHERE id IN (" + JdbcSupport.placeholders(ids.size()) + ")";
        return new HashSet<>(jdbc.queryForList(sql, Long.class, ids.toArray()));
    }
}
