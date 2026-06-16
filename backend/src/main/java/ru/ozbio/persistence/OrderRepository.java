package ru.ozbio.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.ozbio.domain.OrderStatus;
import ru.ozbio.persistence.jdbc.JdbcSupport;
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

    private static final String SELECT_ORDER_DETAILS_BY_ORDER_IDS_PREFIX =
            """
            SELECT od.order_id, od.detail_id, d.name, od.count
            FROM order_detail od
            JOIN detail d ON d.id = od.detail_id
            WHERE od.order_id IN (
            """;

    private static final String SELECT_ORDER_TOOLS =
            """
            SELECT ot.tool_id, t.name, ot.count
            FROM order_tool ot
            JOIN tool t ON t.id = ot.tool_id
            WHERE ot.order_id = ?
            ORDER BY ot.tool_id
            """;

    private static final String SELECT_ORDER_TOOLS_BY_ORDER_IDS_PREFIX =
            """
            SELECT ot.order_id, ot.tool_id, t.name, ot.count
            FROM order_tool ot
            JOIN tool t ON t.id = ot.tool_id
            WHERE ot.order_id IN (
            """;

    private static final String SELECT_ALL_ORDERS =
            """
            SELECT id, status, created_at
            FROM orders
            ORDER BY id
            """;

    private static final String SELECT_ORDERS_FOR_PLANNING =
            """
            SELECT id, status, created_at
            FROM orders
            WHERE status NOT IN ('COMPLETED', 'CANCELLED')
            ORDER BY created_at, id
            """;

    private final JdbcTemplate jdbc;

    public OrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public OrderSummary insert(CreateOrderCommand command) {
        OrderSummary order =
                jdbc.queryForObject(
                        INSERT_ORDER,
                        (rs, rowNum) -> JdbcSupport.mapOrderSummary(rs),
                        OrderStatus.CREATED.name());

        for (CreateOrderCommand.DetailLine detail : command.details()) {
            jdbc.update(INSERT_ORDER_DETAIL, order.id(), detail.detailId(), detail.count());
        }
        for (CreateOrderCommand.ToolLine tool : command.tools()) {
            jdbc.update(INSERT_ORDER_TOOL, order.id(), tool.toolId(), tool.count());
        }

        return order;
    }

    public List<OrderSummary> findAll() {
        return jdbc.query(SELECT_ALL_ORDERS, (rs, rowNum) -> JdbcSupport.mapOrderSummary(rs));
    }

    public List<OrderSummary> findAllForPlanning() {
        return jdbc.query(SELECT_ORDERS_FOR_PLANNING, (rs, rowNum) -> JdbcSupport.mapOrderSummary(rs));
    }

    public List<OrderDetailLine> findDetailsByOrderId(long orderId) {
        return jdbc.query(
                SELECT_ORDER_DETAILS,
                (rs, rowNum) ->
                        new OrderDetailLine(
                                rs.getLong("detail_id"), rs.getString("name"), rs.getInt("count")),
                orderId);
    }

    public Map<Long, List<OrderDetailLine>> findDetailsByOrderIds(Collection<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = List.copyOf(orderIds);
        String sql =
                SELECT_ORDER_DETAILS_BY_ORDER_IDS_PREFIX
                        + JdbcSupport.placeholders(ids.size())
                        + ") ORDER BY od.order_id, od.detail_id";

        Map<Long, List<OrderDetailLine>> result = new HashMap<>();
        jdbc.query(
                sql,
                rs -> {
                    long orderId = rs.getLong("order_id");
                    result.computeIfAbsent(orderId, ignored -> new ArrayList<>())
                            .add(
                                    new OrderDetailLine(
                                            rs.getLong("detail_id"),
                                            rs.getString("name"),
                                            rs.getInt("count")));
                },
                ids.toArray());

        return result;
    }

    public List<OrderToolLine> findToolsByOrderId(long orderId) {
        return jdbc.query(
                SELECT_ORDER_TOOLS,
                (rs, rowNum) ->
                        new OrderToolLine(rs.getLong("tool_id"), rs.getString("name"), rs.getInt("count")),
                orderId);
    }

    public Map<Long, List<OrderToolLine>> findToolsByOrderIds(Collection<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = List.copyOf(orderIds);
        String sql =
                SELECT_ORDER_TOOLS_BY_ORDER_IDS_PREFIX
                        + JdbcSupport.placeholders(ids.size())
                        + ") ORDER BY ot.order_id, ot.tool_id";

        Map<Long, List<OrderToolLine>> result = new HashMap<>();
        jdbc.query(
                sql,
                rs -> {
                    long orderId = rs.getLong("order_id");
                    result.computeIfAbsent(orderId, ignored -> new ArrayList<>())
                            .add(
                                    new OrderToolLine(
                                            rs.getLong("tool_id"),
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

    public boolean toolExists(long toolId) {
        Boolean exists =
                jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM tool WHERE id = ?)", Boolean.class, toolId);
        return Boolean.TRUE.equals(exists);
    }
}
