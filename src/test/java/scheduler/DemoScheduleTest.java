package scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import scheduler.api.OrderPartRequest;
import scheduler.api.OrderRequest;
import scheduler.engine.ScheduleMetrics;
import scheduler.model.Assignment;
import scheduler.model.SetupIntervals;
import java.util.Comparator;
import scheduler.service.AddOrderResult;
import scheduler.service.SchedulerService;
import scheduler.store.JsonScheduleRepository;
import scheduler.store.ScheduleStore;
import scheduler.time.StoreCurrentTimeProvider;

/**
 * Сценарий {@code ./scripts/demo.sh}: заказ З-2026-0142, по {@value #DEMO_QUANTITY} шт. вал + корпус.
 */
class DemoScheduleTest {
    private static final String DEMO_ORDER = "З-2026-0142";
    private static final String PART_SHAFT = "вал-буровой";
    private static final String TASK_ROUGH_TURNING = "черновая-токарка";
    /** Как {@code DEMO_PART_QTY} в {@code scripts/demo.sh}. */
    private static final int DEMO_QUANTITY = 8;

    /** Минимум календарного интервала черновой токарки (несколько штук подряд в сменах). */
    private static final Duration MIN_ROUGH_TURNING_SPAN = Duration.ofHours(5);

    @TempDir
    Path tempDir;

    private SchedulerService service;
    private ScheduleStore store;
    private Instant factoryStart;

    @BeforeEach
    void setUp() throws IOException {
        Path example = Path.of("data/schedule.json.example");
        Path scheduleFile = tempDir.resolve("schedule.json");
        Files.copy(example, scheduleFile);
        store = new JsonScheduleRepository(scheduleFile).loadOrCreate();
        factoryStart = store.factoryStartedAt();
        service = new SchedulerService(store, new JsonScheduleRepository(scheduleFile), new StoreCurrentTimeProvider(store));
    }

    @Test
    void demoOrder0142_schedulesRoughTurningForAllUnits() throws IOException {
        AddOrderResult result = service.addOrder(demoOrderRequest());

        List<Assignment> rough = result.assignmentsForOrder().stream()
                .filter(a -> a.partId().equals(PART_SHAFT))
                .filter(a -> a.taskId().equals(TASK_ROUGH_TURNING))
                .toList();

        assertEquals(DEMO_QUANTITY, rough.size(), "нужна отдельная операция на каждую штуку");
        assertEquals(
                DEMO_QUANTITY,
                ScheduleMetrics.unitsScheduledForTask(
                        DEMO_ORDER, PART_SHAFT, TASK_ROUGH_TURNING, result.assignmentsForOrder()));

        Duration span = ScheduleMetrics.taskSpanOnTimeline(
                DEMO_ORDER, PART_SHAFT, TASK_ROUGH_TURNING, result.assignmentsForOrder());
        assertTrue(
                span.compareTo(MIN_ROUGH_TURNING_SPAN) >= 0,
                () -> "черновая токарка " + DEMO_QUANTITY + " шт.: ожидали >= "
                        + MIN_ROUGH_TURNING_SPAN.toHours()
                        + " ч на шкале, получили "
                        + span.toHours()
                        + " ч");

        long setups = result.assignmentsForOrder().stream()
                .filter(a -> a.partId().equals(PART_SHAFT))
                .filter(a -> SetupIntervals.isSetup(a.taskId()))
                .count();
        assertTrue(
                setups >= 2,
                "переналадка: перед первой черновой и перед чистовой (смена taskId на токарном)");
        assertTrue(result.readyAt().isAfter(factoryStart.plus(Duration.ofHours(8))));
    }

    @Test
    void demoOrder0142_sameMachine_finishTurningAfterAllRoughTurning() throws IOException {
        AddOrderResult result = service.addOrder(demoOrderRequest());

        Instant lastRoughEnd = result.assignmentsForOrder().stream()
                .filter(a -> a.partId().equals(PART_SHAFT) && a.taskId().equals(TASK_ROUGH_TURNING))
                .map(Assignment::plannedEnd)
                .max(Instant::compareTo)
                .orElseThrow();
        Instant firstFinishStart = result.assignmentsForOrder().stream()
                .filter(a -> a.partId().equals(PART_SHAFT) && a.taskId().equals("чистовая-токарка"))
                .map(Assignment::plannedStart)
                .min(Instant::compareTo)
                .orElseThrow();

        assertTrue(
                !firstFinishStart.isBefore(lastRoughEnd),
                "на одном токарном: чистовая только после всего пакета черновой, без наложения");
    }

    @Test
    void demoOrder0142_setupsValidAndContiguousWithWork() throws IOException {
        AddOrderResult result = service.addOrder(demoOrderRequest());

        List<Assignment> setups = result.assignmentsForOrder().stream()
                .filter(a -> SetupIntervals.isSetup(a.taskId()))
                .toList();
        assertTrue(setups.size() >= 2, "переналадка перед первой операцией и при смене taskId на станке");

        for (Assignment setup : setups) {
            assertTrue(
                    setup.plannedStart().isBefore(setup.plannedEnd()),
                    () -> "переналадка: start < end, получили " + setup.plannedStart() + " .. " + setup.plannedEnd());
            Assignment nextWork = result.assignmentsForOrder().stream()
                    .filter(a -> !SetupIntervals.isSetup(a.taskId()))
                    .filter(a -> a.machineId().equals(setup.machineId()))
                    .filter(a -> !a.plannedStart().isBefore(setup.plannedEnd()))
                    .min(Comparator.comparing(Assignment::plannedStart))
                    .orElseThrow();
            assertEquals(
                    0,
                    setup.plannedEnd().compareTo(nextWork.plannedStart()),
                    () -> "после переналадки сразу работа " + nextWork.taskId());
        }
    }

    @Test
    void demoOrder0142_sameMachine_finishMillingAfterAllRoughMilling_onFres() throws IOException {
        AddOrderResult result = service.addOrder(demoOrderRequest());

        Instant lastRoughEnd = result.assignmentsForOrder().stream()
                .filter(a -> a.partId().equals("корпус-бура") && a.taskId().equals("черновая-фрезеровка"))
                .map(Assignment::plannedEnd)
                .max(Instant::compareTo)
                .orElseThrow();
        Instant firstFinishStart = result.assignmentsForOrder().stream()
                .filter(a -> a.partId().equals("корпус-бура") && a.taskId().equals("чистовая-фрезеровка"))
                .map(Assignment::plannedStart)
                .min(Instant::compareTo)
                .orElseThrow();

        assertTrue(
                !firstFinishStart.isBefore(lastRoughEnd),
                "на ФРЕЗ-ЧПУ-01: чистовая только после всего пакета черновой, без чередования штук");

        for (Assignment rough : result.assignmentsForOrder().stream()
                .filter(a -> a.partId().equals("корпус-бура") && a.taskId().equals("черновая-фрезеровка"))
                .toList()) {
            for (Assignment finish : result.assignmentsForOrder().stream()
                    .filter(a -> a.partId().equals("корпус-бура") && a.taskId().equals("чистовая-фрезеровка"))
                    .toList()) {
                assertTrue(
                        !finish.plannedStart().isBefore(rough.plannedEnd())
                                || finish.unitIndex() > rough.unitIndex(),
                        () -> "чистовая шт." + finish.unitIndex() + " не раньше окончания черновой шт."
                                + rough.unitIndex());
            }
        }
    }

    @Test
    void demoOrder0142_overlap_boringStartsBeforeRoughMillingBatchEnds() throws IOException {
        store.setOverlapBatchesEnabled(true);
        AddOrderResult result = service.addOrder(demoOrderRequest());

        Instant lastRoughMillEnd = result.assignmentsForOrder().stream()
                .filter(a -> a.partId().equals("корпус-бура") && a.taskId().equals("черновая-фрезеровка"))
                .map(Assignment::plannedEnd)
                .max(Instant::compareTo)
                .orElseThrow();
        Instant firstBoringStart = result.assignmentsForOrder().stream()
                .filter(a -> a.partId().equals("корпус-бура") && a.taskId().equals("расточивание-отверстий"))
                .map(Assignment::plannedStart)
                .min(Instant::compareTo)
                .orElseThrow();

        assertTrue(
                firstBoringStart.isBefore(lastRoughMillEnd),
                "overlap: расточка на РАСТОЧ-03 стартует до окончания всего пакета черновой фрезеровки на ФРЕЗ-ЧПУ-01");
    }

    private static OrderRequest demoOrderRequest() {
        return new OrderRequest(
                DEMO_ORDER,
                List.of(
                        new OrderPartRequest(PART_SHAFT, DEMO_QUANTITY),
                        new OrderPartRequest("корпус-бура", DEMO_QUANTITY)));
    }
}
