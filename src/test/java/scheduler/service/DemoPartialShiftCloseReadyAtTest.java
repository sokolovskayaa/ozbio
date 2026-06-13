package scheduler.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.io.TempDir;
import scheduler.api.MachineTaskCountRequest;
import scheduler.api.OrderPartRequest;
import scheduler.api.OrderRequest;
import scheduler.api.ShiftCloseRequest;
import scheduler.api.ShiftContextView;
import scheduler.engine.ScheduleMetrics;
import scheduler.store.JsonScheduleRepository;
import scheduler.store.ScheduleStore;
import scheduler.time.StoreCurrentTimeProvider;

/** Почему при частичном закрытии смены readyAt заказа может не сдвинуться (демо 8+8). */
class DemoPartialShiftCloseReadyAtTest {
    private static final String ORDER = "З-2026-0142";

    @TempDir
    Path tempDir;

    private SchedulerService service;
    private ScheduleStore store;

    @BeforeEach
    void setUp() throws IOException {
        Path scheduleFile = tempDir.resolve("schedule.json");
        Files.copy(Path.of("data/schedule.json.example"), scheduleFile);
        store = new JsonScheduleRepository(scheduleFile).loadOrCreate();
        JsonScheduleRepository repository = new JsonScheduleRepository(scheduleFile);
        service = new SchedulerService(store, repository, new StoreCurrentTimeProvider(store));
        service.addOrder(new OrderRequest(
                ORDER,
                List.of(
                        new OrderPartRequest("вал-буровой", 8),
                        new OrderPartRequest("корпус-бура", 8))));
    }

    @Test
    void partialClose_7roughTurn_replansOrder() throws IOException {
        service.setSimulationTime(Instant.parse("2026-05-22T18:00:00Z"));

        // Смена ЧПУ пт 22.05: 08:00–20:00 МСК = 05:00–17:00 UTC
        ShiftContextView ctx = service.shiftContext();
        List<MachineTaskCountRequest> counts = ctx.closeRows().stream()
                .map(row -> {
                    int completed;
                    if ("cnc".equals(row.groupId())) {
                        if ("ТОКАР-ЧПУ-02".equals(row.machineId())
                                && "черновая-токарка".equals(row.taskId())) {
                            completed = 7;
                        } else {
                            completed = Math.min(4, row.plannedCount());
                        }
                    } else {
                        completed = 0;
                    }
                    return new MachineTaskCountRequest(
                            row.machineId(), row.taskId(), completed, row.groupId());
                })
                .toList();
        service.closeShift(new ShiftCloseRequest(
                Instant.parse("2026-05-22T17:00:00Z"),
                null,
                null,
                null,
                counts,
                null,
                true));

        assertTrue(
                ScheduleMetrics.readyAt(ORDER, store.assignments())
                        .isAfter(Instant.parse("2026-05-22T08:00:00Z")));
    }
}
