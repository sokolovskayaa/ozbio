package ru.ozbio.persistence;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.ozbio.persistence.jdbc.JdbcSupport;
import ru.ozbio.service.model.CreateToolCommand;
import ru.ozbio.service.model.ToolDetailLine;
import ru.ozbio.service.model.ToolSummary;

@Repository
public class ToolRepository {

    private static final String INSERT_TOOL =
            """
            INSERT INTO tool (name, assemble_duration)
            VALUES (?, make_interval(secs => ?))
            RETURNING id, name, EXTRACT(EPOCH FROM assemble_duration)::bigint AS assemble_duration_seconds
            """;

    private static final String INSERT_TOOL_DETAIL =
            """
            INSERT INTO tool_detail (tool_id, detail_id, count)
            VALUES (?, ?, ?)
            """;

    private static final String SELECT_TOOL_DETAILS =
            """
            SELECT td.detail_id, d.name, td.count
            FROM tool_detail td
            JOIN detail d ON d.id = td.detail_id
            WHERE td.tool_id = ?
            ORDER BY td.detail_id
            """;

    private static final String SELECT_TOOL_DETAILS_BY_TOOL_IDS_PREFIX =
            """
            SELECT td.tool_id, td.detail_id, d.name, td.count
            FROM tool_detail td
            JOIN detail d ON d.id = td.detail_id
            WHERE td.tool_id IN (
            """;

    private static final String SELECT_ALL_TOOLS =
            """
            SELECT id, name, EXTRACT(EPOCH FROM assemble_duration)::bigint AS assemble_duration_seconds
            FROM tool
            ORDER BY id
            """;

    private final JdbcTemplate jdbc;

    public ToolRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ToolSummary insert(CreateToolCommand command) {
        ToolSummary tool =
                jdbc.queryForObject(
                        INSERT_TOOL,
                        (rs, rowNum) ->
                                new ToolSummary(
                                        rs.getLong("id"),
                                        rs.getString("name"),
                                        Duration.ofSeconds(rs.getLong("assemble_duration_seconds"))),
                        command.name(),
                        command.assembleDuration().toSeconds());

        for (CreateToolCommand.Detail detail : command.details()) {
            jdbc.update(INSERT_TOOL_DETAIL, tool.id(), detail.detailId(), detail.count());
        }

        return tool;
    }

    public List<ToolSummary> findAll() {
        return jdbc.query(
                SELECT_ALL_TOOLS,
                (rs, rowNum) ->
                        new ToolSummary(
                                rs.getLong("id"),
                                rs.getString("name"),
                                Duration.ofSeconds(rs.getLong("assemble_duration_seconds"))));
    }

    public List<ToolDetailLine> findDetailsByToolId(long toolId) {
        return jdbc.query(
                SELECT_TOOL_DETAILS,
                (rs, rowNum) ->
                        new ToolDetailLine(
                                rs.getLong("detail_id"), rs.getString("name"), rs.getInt("count")),
                toolId);
    }

    public Map<Long, List<ToolDetailLine>> findDetailsByToolIds(Collection<Long> toolIds) {
        if (toolIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = List.copyOf(toolIds);
        String sql =
                SELECT_TOOL_DETAILS_BY_TOOL_IDS_PREFIX
                        + JdbcSupport.placeholders(ids.size())
                        + ") ORDER BY td.tool_id, td.detail_id";

        Map<Long, List<ToolDetailLine>> result = new HashMap<>();
        jdbc.query(
                sql,
                rs -> {
                    long toolId = rs.getLong("tool_id");
                    result.computeIfAbsent(toolId, ignored -> new ArrayList<>())
                            .add(
                                    new ToolDetailLine(
                                            rs.getLong("detail_id"),
                                            rs.getString("name"),
                                            rs.getInt("count")));
                },
                ids.toArray());

        return result;
    }

    public boolean detailExists(long detailId) {
        Boolean exists =
                jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM detail WHERE id = ?)", Boolean.class, detailId);
        return Boolean.TRUE.equals(exists);
    }

    public boolean deleteById(long id) {
        jdbc.update("DELETE FROM tool_detail WHERE tool_id = ?", id);
        return jdbc.update("DELETE FROM tool WHERE id = ?", id) > 0;
    }
}
