package scheduler.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import scheduler.api.MachineTaskCountRequest;
import scheduler.api.ShiftCloseRequest;
import scheduler.model.AssignmentStatus;
import scheduler.model.Capability;
import scheduler.model.Task;
import scheduler.store.PartDefinition;
import scheduler.store.ScheduleStore;

class ShiftFactResolverTest {
    private ScheduleStore store;
    private ShiftFactResolver resolver;
    private Instant shiftStart;
    private Instant shiftEnd;

    @BeforeEach
    void setUp() {
        shiftStart = Instant.parse("2026-05-22T05:00:00Z");
        shiftEnd = Instant.parse("2026-05-22T11:00:00Z");
        store = ScheduleStore.empty(shiftStart, true, shiftStart);
        store.setPartDefinition(
                "P1",
                new PartDefinition(
                        10,
                        List.of(new Task("T1", 0, Duration.ofMinutes(60), Capability.MILLING))));
        resolver = new ShiftFactResolver(store);
    }

    @Test
    void resolve_allCompleted_marksEveryPlannedFactCompleted() {
        store.addOrder(new scheduler.model.Order(
                "O1",
                shiftStart,
                List.of(store.createPart("P1", 2)),
                100));
        store.addAssignment(scheduler.model.Assignment.planned(
                "a1", "O1", "P1", 0, "T1", 0, "ФРЕЗ-ЧПУ-01", shiftStart, shiftStart.plusSeconds(3600)));
        store.addAssignment(scheduler.model.Assignment.planned(
                "a2", "O1", "P1", 1, "T1", 0, "ФРЕЗ-ЧПУ-01", shiftStart, shiftStart.plusSeconds(3600)));

        ShiftCloseRequest resolved = resolver.resolve(new ShiftCloseRequest(
                shiftEnd,
                "cnc",
                shiftStart,
                null,
                List.of(new MachineTaskCountRequest("ФРЕЗ-ЧПУ-01", "T1", 2)),
                List.of()));

        assertEquals(2, resolved.operations().size());
        assertTrue(resolved.operations().stream().allMatch(f -> f.completed()));
    }

    @Test
    void resolve_partialCount_firstCompletedRestCancelled() {
        store.addOrder(new scheduler.model.Order(
                "O1",
                shiftStart,
                List.of(store.createPart("P1", 2)),
                100));
        store.addAssignment(scheduler.model.Assignment.planned(
                "a1", "O1", "P1", 0, "T1", 0, "ФРЕЗ-ЧПУ-01", shiftStart, shiftStart.plusSeconds(1800)));
        store.addAssignment(scheduler.model.Assignment.planned(
                "a2",
                "O1",
                "P1",
                1,
                "T1",
                0,
                "ФРЕЗ-ЧПУ-01",
                shiftStart.plusSeconds(2000),
                shiftStart.plusSeconds(3600)));

        ShiftCloseRequest resolved = resolver.resolve(new ShiftCloseRequest(
                shiftEnd,
                "cnc",
                shiftStart,
                null,
                List.of(new MachineTaskCountRequest("ФРЕЗ-ЧПУ-01", "T1", 1)),
                List.of()));

        long completed =
                resolved.operations().stream().filter(f -> f.completed()).count();
        long notDone =
                resolved.operations().stream().filter(f -> !f.completed()).count();
        assertEquals(1, completed);
        assertEquals(1, notDone);
    }

    @Test
    void resolve_defaultsToAllPlannedWhenCountOmitted() {
        store.addOrder(new scheduler.model.Order(
                "O1",
                shiftStart,
                List.of(store.createPart("P1", 1)),
                100));
        store.addAssignment(scheduler.model.Assignment.planned(
                "a1", "O1", "P1", 0, "T1", 0, "ФРЕЗ-ЧПУ-01", shiftStart, shiftEnd));

        ShiftCloseRequest resolved = resolver.resolve(new ShiftCloseRequest(
                shiftEnd, "cnc", shiftStart, null, List.of(), List.of()));

        assertEquals(1, resolved.operations().size());
        assertTrue(resolved.operations().getFirst().completed());
    }

    @Test
    void resolve_rejectsCountAbovePlanned() {
        store.addOrder(new scheduler.model.Order(
                "O1",
                shiftStart,
                List.of(store.createPart("P1", 1)),
                100));
        store.addAssignment(scheduler.model.Assignment.planned(
                "a1", "O1", "P1", 0, "T1", 0, "ФРЕЗ-ЧПУ-01", shiftStart, shiftEnd));

        assertThrows(
                SchedulingException.class,
                () -> resolver.resolve(new ShiftCloseRequest(
                        shiftEnd,
                        "cnc",
                        shiftStart,
                        null,
                        List.of(new MachineTaskCountRequest("ФРЕЗ-ЧПУ-01", "T1", 5)),
                        List.of())));
    }

    @Test
    void resolveAllPending_requiresEveryCloseRow() {
        store.addOrder(new scheduler.model.Order(
                "O1", shiftStart, List.of(store.createPart("P1", 2)), 100));
        store.addAssignment(scheduler.model.Assignment.planned(
                "a1", "O1", "P1", 0, "T1", 0, "ФРЕЗ-ЧПУ-01", shiftStart, shiftEnd));

        assertThrows(
                SchedulingException.class,
                () -> resolver.resolveAllPending(new ShiftCloseRequest(
                        shiftEnd, null, null, null, List.of(), null, true)));
    }

}
