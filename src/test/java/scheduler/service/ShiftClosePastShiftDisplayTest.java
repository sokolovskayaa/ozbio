package scheduler.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import scheduler.model.AssignmentStatus;
import scheduler.model.SetupIntervals;
import scheduler.store.JsonScheduleRepository;
import scheduler.store.ScheduleStore;
import scheduler.time.StoreCurrentTimeProvider;

/** После частичного закрытия смены: в прошлой смене только факт, без отменённых; переналадка пакетом. */
class ShiftClosePastShiftDisplayTest {
  private static final String ORDER = "З-2026-0142";
  private static final Instant SHIFT_END = Instant.parse("2026-05-22T17:00:00Z");

  @TempDir Path tempDir;

  private SchedulerService service;
  private ScheduleStore store;

  @BeforeEach
  void setUp() throws IOException {
    Path scheduleFile = tempDir.resolve("schedule.json");
    Files.copy(Path.of("data/schedule.json.example"), scheduleFile);
    store = new JsonScheduleRepository(scheduleFile).loadOrCreate();
    service =
        new SchedulerService(
            store, new JsonScheduleRepository(scheduleFile), new StoreCurrentTimeProvider(store));
    service.addOrder(
        new OrderRequest(
            ORDER,
            List.of(
                new OrderPartRequest("вал-буровой", 8),
                new OrderPartRequest("корпус-бура", 8))));
  }

  @Test
  void partialClose_twoRoughTurn_hidesCancelledInPastShift_andOneSetupForBatchTail()
      throws IOException {
    service.setSimulationTime(Instant.parse("2026-05-22T18:00:00Z"));
    ShiftContextView ctx = service.shiftContext();

    List<MachineTaskCountRequest> counts =
        ctx.closeRows().stream()
            .map(
                row -> {
                  int completed;
                  if ("cnc".equals(row.groupId())
                      && "ТОКАР-ЧПУ-02".equals(row.machineId())
                      && "черновая-токарка".equals(row.taskId())) {
                    completed = 2;
                  } else if ("cnc".equals(row.groupId())) {
                    completed = 0;
                  } else {
                    completed = 0;
                  }
                  return new MachineTaskCountRequest(
                      row.machineId(), row.taskId(), completed, row.groupId());
                })
            .toList();

    service.closeShift(
        new ShiftCloseRequest(SHIFT_END, null, null, null, counts, null, true));

    long roughInPastShift =
        AssignmentFilters.active(store.assignments()).stream()
            .filter(a -> a.orderId().equals(ORDER))
            .filter(a -> a.machineId().equals("ТОКАР-ЧПУ-02"))
            .filter(a -> a.taskId().equals("черновая-токарка"))
            .filter(a -> !a.plannedStart().isAfter(SHIFT_END))
            .count();
    assertEquals(2, roughInPastShift, "в прошлой смене только 2 черновых токарки по факту");

    long roughCancelled =
        store.assignments().stream()
            .filter(a -> a.status() == AssignmentStatus.CANCELLED)
            .filter(a -> a.machineId().equals("ТОКАР-ЧПУ-02"))
            .filter(a -> a.taskId().equals("черновая-токарка"))
            .count();
    assertTrue(roughCancelled >= 1, "остальные плановые черновые отменены");

    long setupsAfterShift =
        AssignmentFilters.active(store.assignments()).stream()
            .filter(a -> a.machineId().equals("ТОКАР-ЧПУ-02"))
            .filter(a -> SetupIntervals.isSetup(a.taskId()))
            .filter(a -> a.plannedStart().isAfter(SHIFT_END))
            .count();
    assertTrue(
        setupsAfterShift <= 2,
        "на хвосте пакета — не переналадка перед каждой штукой, получили " + setupsAfterShift);
  }
}
