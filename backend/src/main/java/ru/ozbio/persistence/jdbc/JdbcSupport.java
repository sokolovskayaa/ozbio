package ru.ozbio.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ru.ozbio.domain.OrderStatus;
import ru.ozbio.service.model.OrderSummary;

public final class JdbcSupport {

    private JdbcSupport() {}

    public static Instant readInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    public static OrderSummary mapOrderSummary(ResultSet rs) throws SQLException {
        return new OrderSummary(
                rs.getLong("id"),
                OrderStatus.valueOf(rs.getString("status")),
                readInstant(rs.getObject("created_at", OffsetDateTime.class)));
    }

    public static String placeholders(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
        return Stream.generate(() -> "?").limit(count).collect(Collectors.joining(","));
    }
}
