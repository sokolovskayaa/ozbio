package scheduler.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import scheduler.api.OrderPartRequest;
import scheduler.api.OrderRequest;
import scheduler.api.ShiftContextView;
import scheduler.store.JsonScheduleRepository;
import scheduler.store.ScheduleStore;
import scheduler.time.StoreCurrentTimeProvider;

/** Сценарий demo.sh: после 18:00 остаётся смена с планом для ручного закрытия. */
class DemoShiftContextTest {
    @TempDir
    Path tempDir;

    private SchedulerService service;

    @BeforeEach
    void setUp() throws IOException {
        Path scheduleFile = tempDir.resolve("schedule.json");
        Files.copy(Path.of("data/schedule.json.example"), scheduleFile);
        ScheduleStore store = new JsonScheduleRepository(scheduleFile).loadOrCreate();
        service = new SchedulerService(
                store, new JsonScheduleRepository(scheduleFile), new StoreCurrentTimeProvider(store));
    }

    @Test
    void afterDemoDayEnd_stillHasPendingShiftWithPlannedWork() throws IOException {
        service.addOrder(new OrderRequest(
                "З-2026-0142",
                List.of(
                        new OrderPartRequest("вал-буровой", 8),
                        new OrderPartRequest("корпус-бура", 8))));
        service.setSimulationTime(java.time.Instant.parse("2026-05-22T10:00:00Z"));
        service.addOrder(new OrderRequest("З-2026-0148", List.of(new OrderPartRequest("муфта-зажимная", 1))));
        service.setSimulationTime(java.time.Instant.parse("2026-05-22T18:00:00Z"));

        ShiftContextView ctx = service.shiftContext();

        assertTrue(ctx.stale(), "ожидается незакрытая смена ЧПУ с планом");
        assertFalse(ctx.pendingShifts().isEmpty());
        assertTrue(ctx.activeShift() != null);
        assertFalse(ctx.activeShift().machines().isEmpty());
    }
}
