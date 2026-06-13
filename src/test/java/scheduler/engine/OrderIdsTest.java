package scheduler.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderIdsTest {
    private static final Instant T = Instant.parse("2026-05-22T10:00:00Z");

    @Test
    void nextOrderId_startsAtOneWhenNoOrders() {
        assertEquals("З-2026-0001", OrderIds.nextOrderId(T, FactoryZone.ZONE, List.of()));
    }

    @Test
    void nextOrderId_incrementsWithinYear() {
        assertEquals(
                "З-2026-0143",
                OrderIds.nextOrderId(T, FactoryZone.ZONE, List.of("З-2026-0142", "З-2025-9999", "OTHER-1")));
    }
}
