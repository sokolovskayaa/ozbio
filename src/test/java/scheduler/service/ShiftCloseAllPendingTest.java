package scheduler.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import scheduler.api.MachineTaskCountRequest;
import scheduler.api.OrderPartRequest;
import scheduler.api.OrderRequest;
import scheduler.api.ShiftCloseRequest;
import scheduler.api.ShiftContextView;
import scheduler.model.AssignmentStatus;
import scheduler.store.JsonScheduleRepository;
import scheduler.store.ScheduleStore;
import scheduler.time.StoreCurrentTimeProvider;

class ShiftCloseAllPendingTest {
    @TempDir
    Path tempDir;

    private SchedulerService service;
    private ScheduleStore store;

    @BeforeEach
    void setUp() throws IOException {
        Path scheduleFile = tempDir.resolve("schedule.json");
        Files.copy(Path.of("data/schedule.json.example"), scheduleFile);
        store = new JsonScheduleRepository(scheduleFile).loadOrCreate();
        service = new SchedulerService(store, new JsonScheduleRepository(scheduleFile), new StoreCurrentTimeProvider(store));
        service.addOrder(new OrderRequest(
                "З-2026-0142",
                List.of(
                        new OrderPartRequest("вал-буровой", 8),
                        new OrderPartRequest("корпус-бура", 8))));
    }

    @Test
    void closeAllPending_requiresEveryCloseRow() throws IOException {
        service.setSimulationTime(Instant.parse("2026-05-22T18:00:00Z"));
        ShiftContextView ctx = service.shiftContext();
        assertTrue(ctx.stale());
        assertTrue(ctx.closeRows().size() >= 2);

        assertThrows(
                SchedulingException.class,
                () -> service.closeShift(new ShiftCloseRequest(
                        Instant.parse("2026-05-22T17:00:00Z"),
                        null,
                        null,
                        null,
                        List.of(new MachineTaskCountRequest(
                                "ТОКАР-ЧПУ-02", "черновая-токарка", 1, "cnc")),
                        null,
                        true)));
    }

    @Test
    void closeAllPending_partialCount_marksRestCancelled() throws IOException {
        service.setSimulationTime(Instant.parse("2026-05-22T18:00:00Z"));
        ShiftContextView ctx = service.shiftContext();
        List<MachineTaskCountRequest> counts = ctx.closeRows().stream()
                .map(row -> new MachineTaskCountRequest(
                        row.machineId(),
                        row.taskId(),
                        row.machineId().equals("ТОКАР-ЧПУ-02")
                                        && row.taskId().equals("черновая-токарка")
                                ? 1
                                : 0,
                        row.groupId()))
                .toList();

        service.closeShift(new ShiftCloseRequest(
                Instant.parse("2026-05-22T17:00:00Z"),
                null,
                null,
                null,
                counts,
                null,
                true));

        long completedRough =
                store.assignments().stream()
                        .filter(a -> a.taskId().equals("черновая-токарка"))
                        .filter(a -> a.status() == AssignmentStatus.COMPLETED)
                        .count();
        long cancelledRough =
                store.assignments().stream()
                        .filter(a -> a.taskId().equals("черновая-токарка"))
                        .filter(a -> a.status() == AssignmentStatus.CANCELLED)
                        .count();
        assertEquals(1, completedRough);
        assertTrue(cancelledRough >= 1);
    }

    @Test
    void closeAllPending_replansOnceAfterAllGroupFacts() throws IOException {
        service.setSimulationTime(Instant.parse("2026-05-22T18:00:00Z"));
        ShiftContextView ctx = service.shiftContext();
        List<MachineTaskCountRequest> counts = ctx.closeRows().stream()
                .map(row -> new MachineTaskCountRequest(
                        row.machineId(), row.taskId(), row.plannedCount(), row.groupId()))
                .toList();

        ShiftCloseResult result = service.closeShift(new ShiftCloseRequest(
                Instant.parse("2026-05-22T17:00:00Z"),
                null,
                null,
                null,
                counts,
                null,
                true));

        assertTrue(result.replannedOrderIds().contains("З-2026-0142"));
        assertTrue(store.lastClosedShiftEnd("cnc") != null);
        assertFalse(service.shiftContext().stale());
    }
}
