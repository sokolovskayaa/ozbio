package scheduler.engine.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class OrderPrioritiesTest {
    @Test
    void fromCreatedAt_earlierOrderHasHigherPriority() {
        Instant earlier = Instant.parse("2026-05-22T08:00:00Z");
        Instant later = Instant.parse("2026-05-23T08:00:00Z");
        assertTrue(OrderPriorities.fromCreatedAt(earlier) > OrderPriorities.fromCreatedAt(later));
    }

    @Test
    void resolve_usesStoredWhenPositive() {
        Instant createdAt = Instant.parse("2026-05-22T08:00:00Z");
        assertEquals(999, OrderPriorities.resolve(createdAt, 999));
    }

    @Test
    void resolve_derivesFromDateWhenZero() {
        Instant createdAt = Instant.parse("2026-05-22T08:00:00Z");
        assertEquals(OrderPriorities.fromCreatedAt(createdAt), OrderPriorities.resolve(createdAt, 0));
    }
}
