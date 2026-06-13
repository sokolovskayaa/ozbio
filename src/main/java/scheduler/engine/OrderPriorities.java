package scheduler.engine;

import java.time.Instant;
import java.util.Comparator;
import scheduler.model.Order;

/**
 * Приоритет заказа: большее значение — важнее (раньше в очереди планирования).
 * Пока вычисляется только из {@link Order#createdAt()}; позже можно задавать вручную.
 */
public final class OrderPriorities {
    /** Секунды эпохи, от которых вычитаем {@code createdAt} (раньше заказ → выше приоритет). */
    private static final long PRIORITY_EPOCH_SECOND = 2_147_483_647L;

    public static final Comparator<Order> QUEUE_ORDER = Comparator.comparingInt(Order::priority)
            .reversed()
            .thenComparing(Order::createdAt)
            .thenComparing(Order::orderId);

    private OrderPriorities() {}

    public static int fromCreatedAt(Instant createdAt) {
        long epochSec = createdAt.getEpochSecond();
        if (epochSec >= PRIORITY_EPOCH_SECOND) {
            return 0;
        }
        return (int) (PRIORITY_EPOCH_SECOND - epochSec);
    }

    /** Приоритет для загрузки из JSON: сохранённое значение или расчёт по дате. */
    public static int resolve(Instant createdAt, int storedPriority) {
        if (storedPriority > 0) {
            return storedPriority;
        }
        return fromCreatedAt(createdAt);
    }
}
