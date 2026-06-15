package ru.ozbio.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.ozbio.service.model.MachineSummary;
import ru.ozbio.service.model.MachineTypeSummary;

@Repository
public class MachineRepository {

    private static final String INSERT_MACHINE_TYPE =
            """
            INSERT INTO machine_type (type_name)
            VALUES (?)
            RETURNING id, type_name
            """;

    private static final String INSERT_MACHINE =
            """
            INSERT INTO machine (machine_type_id)
            VALUES (?)
            RETURNING id, machine_type_id
            """;

    private static final String SELECT_MACHINE =
            """
            SELECT m.id, m.machine_type_id, mt.type_name
            FROM machine m
            JOIN machine_type mt ON mt.id = m.machine_type_id
            WHERE m.id = ?
            """;

    private static final String SELECT_ALL_MACHINES =
            """
            SELECT m.id, m.machine_type_id, mt.type_name
            FROM machine m
            JOIN machine_type mt ON mt.id = m.machine_type_id
            ORDER BY m.id
            """;

    private static final String SELECT_ALL_MACHINE_TYPES =
            """
            SELECT id, type_name
            FROM machine_type
            ORDER BY id
            """;

    private final JdbcTemplate jdbc;

    public MachineRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public MachineTypeSummary insertType(String typeName) {
        return jdbc.queryForObject(
                INSERT_MACHINE_TYPE,
                (rs, rowNum) -> new MachineTypeSummary(rs.getLong("id"), rs.getString("type_name")),
                typeName);
    }

    public long insertMachine(long machineTypeId) {
        return jdbc.queryForObject(INSERT_MACHINE, Long.class, machineTypeId);
    }

    public Optional<MachineSummary> findMachineById(long id) {
        return jdbc.query(
                        SELECT_MACHINE,
                        (rs, rowNum) ->
                                new MachineSummary(
                                        rs.getLong("id"),
                                        rs.getLong("machine_type_id"),
                                        rs.getString("type_name")),
                        id)
                .stream()
                .findFirst();
    }

    public List<MachineTypeSummary> findAllTypes() {
        return jdbc.query(
                SELECT_ALL_MACHINE_TYPES,
                (rs, rowNum) -> new MachineTypeSummary(rs.getLong("id"), rs.getString("type_name")));
    }

    public List<MachineSummary> findAllMachines() {
        return jdbc.query(
                SELECT_ALL_MACHINES,
                (rs, rowNum) ->
                        new MachineSummary(
                                rs.getLong("id"),
                                rs.getLong("machine_type_id"),
                                rs.getString("type_name")));
    }

    public boolean machineTypeExists(long id) {
        Boolean exists =
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM machine_type WHERE id = ?)", Boolean.class, id);
        return Boolean.TRUE.equals(exists);
    }

    public boolean machineExists(long id) {
        Boolean exists =
                jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM machine WHERE id = ?)", Boolean.class, id);
        return Boolean.TRUE.equals(exists);
    }

    public boolean machineTypeIsReferenced(long id) {
        Boolean hasMachines =
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM machine WHERE machine_type_id = ?)", Boolean.class, id);
        if (Boolean.TRUE.equals(hasMachines)) {
            return true;
        }
        Boolean hasOperations =
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM operation WHERE machine_type_id = ?)", Boolean.class, id);
        return Boolean.TRUE.equals(hasOperations);
    }

    public boolean deleteTypeById(long id) {
        return jdbc.update("DELETE FROM machine_type WHERE id = ?", id) > 0;
    }

    public boolean deleteMachineById(long id) {
        return jdbc.update("DELETE FROM machine WHERE id = ?", id) > 0;
    }
}
