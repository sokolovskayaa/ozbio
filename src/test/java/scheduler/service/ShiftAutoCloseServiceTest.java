package scheduler.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import scheduler.api.ShiftContextView;
import scheduler.model.Capability;
import scheduler.model.Task;
import scheduler.store.JsonScheduleRepository;
import scheduler.store.PartDefinition;
import scheduler.store.ScheduleStore;
import scheduler.time.StoreCurrentTimeProvider;

class ShiftAutoCloseServiceTest {
    @TempDir
    java.nio.file.Path tempDir;

    private ScheduleStore store;
    private ShiftAutoCloseService autoClose;
    private ShiftContextService contextService;
    private JsonScheduleRepository repository;

    @BeforeEach
    void setUp() {
        Instant factoryStart = Instant.parse("2026-05-22T05:00:00Z");
        store = ScheduleStore.empty(factoryStart, true, factoryStart);
        store.setPartDefinition(
                "P1",
                new PartDefinition(
                        10,
                        List.of(new Task("T1", 0, Duration.ofMinutes(60), Capability.MILLING))));
        repository = new JsonScheduleRepository(tempDir.resolve("schedule.json"));
        var time = new StoreCurrentTimeProvider(store);
        autoClose = new ShiftAutoCloseService(store, repository, time);
        contextService = new ShiftContextService(store, time);
    }

    @Test
    void closeEmptyPendingShifts_marksLastClosedWithoutWork() throws IOException {
        Instant endOfDay = Instant.parse("2026-05-22T18:00:00Z");
        store.setSimulationCurrentTime(endOfDay);

        int closed = autoClose.closeEmptyPendingShifts();

        assertTrue(closed >= 1);
        assertTrue(store.lastClosedShiftEnd("heavy") != null);
        assertTrue(Files.exists(tempDir.resolve("schedule.json")));
    }

    @Test
    void closeEmptyPendingShifts_skipsShiftWithPlannedWork() throws IOException {
        Instant shiftStart = Instant.parse("2026-05-22T05:00:00Z");
        Instant shiftEnd = Instant.parse("2026-05-22T17:00:00Z");
        store.addOrder(new scheduler.model.Order(
                "O1", shiftStart, List.of(store.createPart("P1", 1)), 100));
        store.addAssignment(scheduler.model.Assignment.planned(
                "a1", "O1", "P1", 0, "T1", 0, "ФРЕЗ-ЧПУ-01", shiftStart, shiftEnd));
        store.setSimulationCurrentTime(Instant.parse("2026-05-22T18:00:00Z"));

        autoClose.closeEmptyPendingShifts();

        ShiftContextView ctx = contextService.build();
        assertTrue(ctx.stale());
        assertTrue(ctx.pendingShifts().stream().anyMatch(s -> "cnc".equals(s.groupId())));
    }

    @Test
    void closeEmptyPendingShifts_clearsStaleWhenOnlyEmptyShiftsPending() throws IOException {
        store.setSimulationCurrentTime(Instant.parse("2026-05-22T18:00:00Z"));
        autoClose.closeEmptyPendingShifts();

        ShiftContextView ctx = contextService.build();
        assertFalse(ctx.stale());
        assertEquals(0, ctx.pendingShiftCount());
    }

    @Test
    void closeEmptyPendingShifts_loopsUntilNoEmptyPendingLeft() throws IOException {
        store.setSimulationCurrentTime(Instant.parse("2026-05-25T18:00:00Z"));

        int closed = autoClose.closeEmptyPendingShifts();

        assertTrue(closed >= 3);
        assertFalse(contextService.build().stale());
    }
}
