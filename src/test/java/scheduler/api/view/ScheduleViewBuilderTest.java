package scheduler.api.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import scheduler.model.schedule.Assignment;
import scheduler.model.schedule.AssignmentStatus;
import scheduler.model.machine.Capability;
import scheduler.model.order.Order;
import scheduler.model.order.Part;
import scheduler.model.order.Task;
import scheduler.store.core.PartDefinition;
import scheduler.store.core.ScheduleStore;
import scheduler.time.FixedTimeProvider;

class ScheduleViewBuilderTest {
    @Test
    void build_excludesCancelledAssignments() {
        Instant factory = Instant.parse("2026-05-22T08:00:00Z");
        ScheduleStore store = ScheduleStore.empty(factory);
        store.setPartDefinition(
                "P1",
                new PartDefinition(
                        1, List.of(new Task("T1", 0, Duration.ofMinutes(10), Capability.MILLING))));
        store.addOrder(new Order(
                "O1",
                factory,
                List.of(new Part(
                        "P1",
                        2,
                        List.of(new Task("T1", 0, Duration.ofMinutes(10), Capability.MILLING)))),
                1));
        store.addAssignment(Assignment.planned(
                "a1", "O1", "P1", 0, "T1", 0, "ФРЕЗ-ЧПУ-01", factory, factory.plus(Duration.ofMinutes(10))));
        store.addAssignment(new Assignment(
                "a2",
                "O1",
                "P1",
                1,
                "T1",
                0,
                "ФРЕЗ-ЧПУ-01",
                factory,
                factory.plus(Duration.ofMinutes(20)),
                AssignmentStatus.CANCELLED,
                null,
                null));

        ScheduleView view = ScheduleViewBuilder.build(store, new FixedTimeProvider(factory));
        long inView = view.orders().stream()
                .flatMap(o -> o.parts().stream())
                .flatMap(p -> p.assignments().stream())
                .count();

        assertEquals(1, inView);
        assertTrue(view.orders().getFirst().parts().getFirst().assignments().stream()
                .noneMatch(a -> "CANCELLED".equals(a.status())));
    }
}
