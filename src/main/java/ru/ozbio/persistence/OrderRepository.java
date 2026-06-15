package ru.ozbio.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.ozbio.domain.OrderStatus;
import ru.ozbio.service.model.CreateOrderCommand;
import ru.ozbio.service.model.OrderDetailLine;
import ru.ozbio.service.model.OrderSummary;
import ru.ozbio.service.model.OrderToolLine;

@Repository
public class OrderRepository {

    private static final String INSERT_ORDER =
            """
            INSERT INTO orders (status)
            VALUES (CAST(? AS order_status))
            RETURNING id, status, created_at
            """;

    private static final String INSERT_ORDER_DETAIL =
            """
            INSERT INTO order_detail (order_id, detail_id, count)
            VALUES (?, ?, ?)
            """;

    private static final String INSERT_ORDER_TOOL =
            """
            INSERT INTO order_tool (order_id, tool_id, count)
            VALUES (?, ?, ?)
            """;

    private static final String SELECT_ORDER_DETAILS =
            """
            SELECT od.detail_id, d.name, od.count
            FROM order_detail od
            JOIN detail d ON d.id = od.detail_id
            WHERE od.order_id = ?
            ORDER BY od.detail_id
            """;

    private static final String SELECT_ORDER_TOOLS =
            """
            SELECT ot.tool_id, t.name, ot.count
            FROM order_tool ot
            JOIN tool t ON t.id = ot.tool_id
            WHERE ot.order_id = ?
            ORDER BY ot.tool_id
            """;

    private final JdbcTemplate jdbc;

    public OrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public OrderSummary insert(CreateOrderCommand command) {
        OrderSummary order =
                jdbc.queryForObject(
                        INSERT_ORDER,
                        (rs, rowNum) ->
                                new OrderSummary(
                                        rs.getLong("id"),
                                        OrderStatus.valueOf(rs.getString("status")),
                                        readInstant(rs.getObject("created_at", OffsetDateTime.class))),
                        OrderStatus.CREATED.name());

        for (CreateOrderCommand.DetailLine detail : command.details()) {
            jdbc.update(INSERT_ORDER_DETAIL, order.id(), detail.detailId(), detail.count());
        }
        for (CreateOrderCommand.ToolLine tool : command.tools()) {
            jdbc.update(INSERT_ORDER_TOOL, order.id(), tool.toolId(), tool.count());
        }

        return order;
    }

    public List<OrderDetailLine> findDetailsByOrderId(long orderId) {
        return jdbc.query(
                SELECT_ORDER_DETAILS,
                (rs, rowNum) ->
                        new OrderDetailLine(
                                rs.getLong("detail_id"), rs.getString("name"), rs.getInt("count")),
                orderId);
    }

    public List<OrderToolLine> findToolsByOrderId(long orderId) {
        return jdbc.query(
                SELECT_ORDER_TOOLS,
                (rs, rowNum) ->
                        new OrderToolLine(rs.getLong("tool_id"), rs.getString("name"), rs.getInt("count")),
                orderId);
    }

    public boolean detailExists(long detailId) {
        Boolean exists =
                jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM detail WHERE id = ?)", Boolean.class, detailId);
        return Boolean.TRUE.equals(exists);
    }

    public boolean toolExists(long toolId) {
        Boolean exists =
                jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM tool WHERE id = ?)", Boolean.class, toolId);
        return Boolean.TRUE.equals(exists);
    }

    private static Instant readInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
