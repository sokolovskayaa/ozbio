package scheduler.engine.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import scheduler.model.schedule.Assignment;
import scheduler.model.schedule.AssignmentStatus;
import scheduler.model.order.Part;
import scheduler.model.order.Task;
import scheduler.model.machine.Capability;
import scheduler.model.order.Order;
import scheduler.engine.policy.OrderPriorities;

class ScheduleMetricsTest {
    @Test
    void isWorkTaskDone_onlyWhenCompleted() {
        Part part = new Part("P1", 1, List.of(new Task("T1", 0, Duration.ofMinutes(10), Capability.MILLING)));
        List<Assignment> assignments = List.of(Assignment.planned(
                "a1", "O1", "P1", 0, "T1", 0, "M1",
                Instant.parse("2026-05-22T08:00:00Z"),
                Instant.parse("2026-05-22T09:00:00Z")));
        assertFalse(TaskReadiness.isWorkTaskDone("O1", part, 0, "T1", assignments));

        Assignment completed = new Assignment(
                "a1", "O1", "P1", 0, "T1", 0, "M1",
                Instant.parse("2026-05-22T08:00:00Z"),
                Instant.parse("2026-05-22T09:00:00Z"),
                AssignmentStatus.COMPLETED,
                Instant.parse("2026-05-22T08:00:00Z"),
                Instant.parse("2026-05-22T08:30:00Z"));
        assertTrue(TaskReadiness.isWorkTaskDone("O1", part, 0, "T1", List.of(completed)));
    }

    @Test
    void previousTaskEnd_usesActualEndForCompleted() {
        Instant factory = Instant.parse("2026-05-22T08:00:00Z");
        Part part = new Part("P1", 2, List.of(new Task("T1", 0, Duration.ofMinutes(10), Capability.MILLING)));
        Order order = new Order("O1", factory, List.of(part), OrderPriorities.fromCreatedAt(factory));
        Assignment unit0 = new Assignment(
                "a1", "O1", "P1", 0, "T1", 0, "M1",
                factory, factory.plus(Duration.ofHours(1)),
                AssignmentStatus.COMPLETED,
                factory, factory.plus(Duration.ofMinutes(30)));
        Instant prev =
                TaskReadiness.previousTaskEnd(order, part, 1, 0, List.of(unit0), factory, "M1");
        assertEquals(factory.plus(Duration.ofMinutes(30)), prev);
    }

    @Test
    void isTaskReady_finishMillingBlockedUntilAllRoughMillingPlanned_onSharedMachine() {
        Instant factory = Instant.parse("2026-05-22T08:00:00Z");
        Task rough = new Task("черновая-фрезеровка", 0, Duration.ofMinutes(90), Capability.MILLING);
        Task boring = new Task("расточивание-отверстий", 1, Duration.ofMinutes(120), Capability.DEEP_BORING);
        Task finish = new Task("чистовая-фрезеровка", 2, Duration.ofMinutes(60), Capability.MILLING);
        Part part = new Part("корпус-бура", 8, List.of(rough, boring, finish));
        Order order = new Order("O1", factory, List.of(part), OrderPriorities.fromCreatedAt(factory));

        List<Assignment> assignments = List.of(
                Assignment.planned(
                        "r0",
                        "O1",
                        "корпус-бура",
                        0,
                        "черновая-фрезеровка",
                        0,
                        "ФРЕЗ-ЧПУ-01",
                        factory,
                        factory.plus(Duration.ofMinutes(90))),
                Assignment.planned(
                        "b0",
                        "O1",
                        "корпус-бура",
                        0,
                        "расточивание-отверстий",
                        1,
                        "РАСТОЧ-03",
                        factory.plus(Duration.ofHours(2)),
                        factory.plus(Duration.ofHours(4))));

        assertFalse(
                TaskReadiness.isTaskReady(order, part, 0, finish, assignments, factory),
                "чистовая на том же ФРЕЗ: нужна вся партия черновой, не только расточка шт.0");
    }
}
