package scheduler.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import scheduler.model.Assignment;
import scheduler.model.AssignmentStatus;
import scheduler.model.Machine;
import scheduler.model.MachineStatus;
import scheduler.model.Part;
import scheduler.model.Task;
import scheduler.model.Capability;
import scheduler.model.Order;
import scheduler.store.ScheduleStore;

class ScheduleMetricsTest {
    @Test
    void isWorkTaskDone_onlyWhenCompleted() {
        Part part = new Part("P1", 1, List.of(new Task("T1", 0, Duration.ofMinutes(10), Capability.MILLING)));
        List<Assignment> assignments = List.of(Assignment.planned(
                "a1", "O1", "P1", 0, "T1", 0, "M1",
                Instant.parse("2026-05-22T08:00:00Z"),
                Instant.parse("2026-05-22T09:00:00Z")));
        assertFalse(ScheduleMetrics.isWorkTaskDone("O1", part, 0, "T1", assignments));

        Assignment completed = new Assignment(
                "a1", "O1", "P1", 0, "T1", 0, "M1",
                Instant.parse("2026-05-22T08:00:00Z"),
                Instant.parse("2026-05-22T09:00:00Z"),
                AssignmentStatus.COMPLETED,
                Instant.parse("2026-05-22T08:00:00Z"),
                Instant.parse("2026-05-22T08:30:00Z"));
        assertTrue(ScheduleMetrics.isWorkTaskDone("O1", part, 0, "T1", List.of(completed)));
    }

    @Test
    void previousTaskEnd_usesActualEndForCompleted() {
        Instant factory = Instant.parse("2026-05-22T08:00:00Z");
        ScheduleStore store = ScheduleStore.empty(factory, true, factory);
        Part part = new Part("P1", 2, List.of(new Task("T1", 0, Duration.ofMinutes(10), Capability.MILLING)));
        Order order = new Order("O1", factory, List.of(part), OrderPriorities.fromCreatedAt(factory));
        Assignment unit0 = new Assignment(
                "a1", "O1", "P1", 0, "T1", 0, "M1",
                factory, factory.plus(Duration.ofHours(1)),
                AssignmentStatus.COMPLETED,
                factory, factory.plus(Duration.ofMinutes(30)));
        Instant prev =
                ScheduleMetrics.previousTaskEnd(order, part, 1, 0, List.of(unit0), factory, store, "M1");
        assertEquals(factory.plus(Duration.ofMinutes(30)), prev);
    }

    @Test
    void isTaskReady_finishMillingBlockedUntilAllRoughMillingPlanned_onSharedMachine() {
        Instant factory = Instant.parse("2026-05-22T08:00:00Z");
        ScheduleStore store = ScheduleStore.empty(factory, true, factory);
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
                ScheduleMetrics.isTaskReady(order, part, 0, finish, assignments, factory, store),
                "чистовая на том же ФРЕЗ: нужна вся партия черновой, не только расточка шт.0");
    }

    @Test
    void isTaskReady_finishTurnAfterFirstRoughOnly_whenTwoTokars() {
        Instant factory = Instant.parse("2026-05-22T08:00:00Z");
        ScheduleStore store = ScheduleStore.empty(factory, true, factory);
        store.setOverlapBatchesEnabled(true);
        store.machines()
                .add(new Machine(
                        "ТОКАР-ЧПУ-03", "cnc", Set.of(Capability.TURNING), factory, MachineStatus.IDLE));
        Task rough = new Task("черновая-токарка", 0, Duration.ofMinutes(70), Capability.TURNING);
        Task finish = new Task("чистовая-токарка", 1, Duration.ofMinutes(45), Capability.TURNING);
        Part part = new Part("вал-буровой", 12, List.of(rough, finish));
        Order order = new Order("O1", factory, List.of(part), 1);
        List<Assignment> assignments = List.of(Assignment.planned(
                "r0",
                "O1",
                "вал-буровой",
                0,
                "черновая-токарка",
                0,
                "ТОКАР-ЧПУ-02",
                factory,
                factory.plus(Duration.ofMinutes(70))));

        assertFalse(
                ScheduleMetrics.isTaskReady(order, part, 0, finish, assignments, factory, store),
                "чистовая шт.0 — только после всего пакета черновой в плане (непрерывный пакет на 03)");
    }

    @Test
    void previousTaskEnd_finishOnEmptyTokar_usesOverlapNotGlobalRoughEnd() {
        Instant factory = Instant.parse("2026-05-22T08:00:00Z");
        ScheduleStore store = ScheduleStore.empty(factory, true, factory);
        store.setOverlapBatchesEnabled(true);
        store.machines()
                .add(new Machine(
                        "ТОКАР-ЧПУ-03", "cnc", Set.of(Capability.TURNING), factory, MachineStatus.IDLE));
        Task rough = new Task("черновая-токарка", 0, Duration.ofMinutes(70), Capability.TURNING);
        Task finish = new Task("чистовая-токарка", 1, Duration.ofMinutes(45), Capability.TURNING);
        Part part = new Part("вал-буровой", 12, List.of(rough, finish));
        Order order = new Order("O1", factory, List.of(part), 1);
        Instant roughEnd = factory.plus(Duration.ofHours(20));
        List<Assignment> assignments = List.of(Assignment.planned(
                "r0",
                "O1",
                "вал-буровой",
                0,
                "черновая-токарка",
                0,
                "ТОКАР-ЧПУ-02",
                factory,
                roughEnd));

        Instant prevOn03 =
                ScheduleMetrics.previousTaskEnd(order, part, 0, 1, assignments, factory, store, "ТОКАР-ЧПУ-03");

        assertEquals(roughEnd, prevOn03, "чистовая шт.0 не раньше конца черновой шт.0");
    }
}
