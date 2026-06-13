package scheduler;

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
import scheduler.engine.AssignmentFilters;
import scheduler.model.Assignment;
import scheduler.model.SetupIntervals;
import scheduler.service.SchedulerService;
import scheduler.store.JsonScheduleRepository;
import scheduler.store.ScheduleStore;
import scheduler.time.StoreCurrentTimeProvider;

/**
 * Сквозные сценарии зафиксированной версии MVP: overlap на дублях станков, закрытие всех смен,
 * отображение фактов без отменённых, переплан.
 */
class MvpRegressionTest {
    private static final String ORDER = "З-MVP";
    private static final Instant SHIFT_END = Instant.parse("2026-05-22T17:00:00Z");

    @TempDir
    Path tempDir;

    private ScheduleStore store;
    private SchedulerService service;

    @BeforeEach
    void setUp() throws IOException {
        Path scheduleFile = tempDir.resolve("schedule.json");
        Files.copy(Path.of("data/schedule-duplicate-machines.example.json"), scheduleFile);
        store = new JsonScheduleRepository(scheduleFile).loadOrCreate();
        store.setOverlapBatchesEnabled(true);
        service =
                new SchedulerService(
                        store, new JsonScheduleRepository(scheduleFile), new StoreCurrentTimeProvider(store));
    }

    @Test
    void duplicateMachines_overlapPreservedAfterPartialShiftCloseAndReplan() throws IOException {
        service.addOrder(new OrderRequest(
                ORDER,
                List.of(
                        new OrderPartRequest("вал-буровой", 12),
                        new OrderPartRequest("корпус-бура", 12))));
        service.setSimulationTime(Instant.parse("2026-05-22T18:00:00Z"));

        Instant finishBeforeClose = firstFinishStart("ТОКАР-ЧПУ-03", "чистовая-токарка");
        Instant roughEndBeforeClose = lastRoughEnd("ТОКАР-ЧПУ-02", "черновая-токарка");
        assertTrue(finishBeforeClose.isBefore(roughEndBeforeClose));

        ShiftContextView ctx = service.shiftContext();
        List<MachineTaskCountRequest> counts = ctx.closeRows().stream()
                .map(row -> {
                    int completed = 0;
                    if ("cnc".equals(row.groupId())
                            && "ТОКАР-ЧПУ-02".equals(row.machineId())
                            && "черновая-токарка".equals(row.taskId())) {
                        completed = 2;
                    }
                    return new MachineTaskCountRequest(
                            row.machineId(), row.taskId(), completed, row.groupId());
                })
                .toList();

        service.closeShift(new ShiftCloseRequest(SHIFT_END, null, null, null, counts, null, true));

        long roughVisiblePastShift =
                AssignmentFilters.active(store.assignments()).stream()
                        .filter(a -> a.orderId().equals(ORDER))
                        .filter(a -> a.taskId().equals("черновая-токарка"))
                        .filter(a -> !a.plannedStart().isAfter(SHIFT_END))
                        .count();
        assertTrue(roughVisiblePastShift <= 4, "в прошлой смене только факт черновой, не весь план");

        long cancelledVisible =
                store.assignments().stream()
                        .filter(a -> a.status() == scheduler.model.AssignmentStatus.CANCELLED)
                        .count();
        assertTrue(cancelledVisible > 0);

        Instant finishAfter = firstFinishStart("ТОКАР-ЧПУ-03", "чистовая-токарка");
        Instant roughEndAfter = lastRoughEnd("ТОКАР-ЧПУ-02", "черновая-токарка");
        assertTrue(
                finishAfter.isBefore(roughEndAfter),
                "после переплана overlap черновая/чистовая на разных токарных сохраняется");

        long setupsOn03 =
                AssignmentFilters.active(store.assignments()).stream()
                        .filter(a -> a.machineId().equals("ТОКАР-ЧПУ-03"))
                        .filter(a -> SetupIntervals.isSetup(a.taskId()))
                        .filter(a -> a.plannedStart().isAfter(SHIFT_END))
                        .count();
        assertTrue(setupsOn03 <= 3, "не переналадка перед каждой штукой на хвосте");
    }

    private Instant firstFinishStart(String machineId, String taskId) {
        return AssignmentFilters.work(store.assignments()).stream()
                .filter(a -> a.orderId().equals(ORDER))
                .filter(a -> a.machineId().equals(machineId))
                .filter(a -> a.taskId().equals(taskId))
                .map(Assignment::plannedStart)
                .min(Instant::compareTo)
                .orElseThrow();
    }

    private Instant lastRoughEnd(String machineId, String taskId) {
        return AssignmentFilters.work(store.assignments()).stream()
                .filter(a -> a.orderId().equals(ORDER))
                .filter(a -> a.machineId().equals(machineId))
                .filter(a -> a.taskId().equals(taskId))
                .map(Assignment::plannedEnd)
                .max(Instant::compareTo)
                .orElseThrow();
    }
}
