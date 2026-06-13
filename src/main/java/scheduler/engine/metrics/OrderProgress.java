package scheduler.engine.metrics;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import scheduler.model.schedule.Assignment;
import scheduler.model.order.Order;
import scheduler.store.core.ScheduleStore;
import scheduler.time.CurrentTimeProvider;

/** Сроки готовности заказов и деталей. */
public final class OrderProgress {
    private OrderProgress() {}

    public static Instant readyAt(String orderId, List<Assignment> assignments) {
        return AssignmentFilters.active(assignments).stream()
                .filter(a -> a.orderId().equals(orderId))
                .map(Assignment::effectiveEnd)
                .max(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException("No assignments for order " + orderId));
    }

    public static Instant partReadyAt(String orderId, String partId, List<Assignment> assignments) {
        return AssignmentFilters.active(assignments).stream()
                .filter(a -> a.orderId().equals(orderId) && a.partId().equals(partId))
                .map(Assignment::effectiveEnd)
                .max(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException("No assignments for part " + partId));
    }

    public static Instant orderStart(Order order, ScheduleStore store, CurrentTimeProvider time) {
        Instant base = order.createdAt().isBefore(store.factoryStartedAt())
                ? store.factoryStartedAt()
                : order.createdAt();
        Instant now = time.now();
        return base.isBefore(now) ? now : base;
    }
}
