package scheduler.model.order;

import java.time.Instant;
import java.util.List;

/**
 * @param priority чем больше, тем раньше заказ в очереди планирования; пока из {@link #createdAt()}
 */
public record Order(String orderId, Instant createdAt, List<Part> parts, int priority) {}
