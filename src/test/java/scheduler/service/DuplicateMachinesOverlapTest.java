package scheduler.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import scheduler.api.OrderPartRequest;
import scheduler.api.OrderRequest;
import scheduler.engine.AssignmentFilters;
import scheduler.model.Assignment;
import scheduler.store.JsonScheduleRepository;
import scheduler.store.ScheduleStore;
import scheduler.time.StoreCurrentTimeProvider;

/** Чистовая на втором токарном может начаться до конца всей черновой на первом (≥2 станка TURNING). */
class DuplicateMachinesOverlapTest {
    private static final String ORDER = "З-дубль-станки";
    private static final int QTY = 12;

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
        service.addOrder(
                new OrderRequest(
                        ORDER,
                        List.of(
                                new OrderPartRequest("вал-буровой", QTY),
                                new OrderPartRequest("корпус-бура", QTY))));
    }

    @Test
    void finishTurnOnSecondTokar_startsBeforeLastRoughEndsOnFirst() {
        Instant lastRoughEnd =
                AssignmentFilters.work(store.assignments()).stream()
                        .filter(a -> a.orderId().equals(ORDER))
                        .filter(a -> a.machineId().equals("ТОКАР-ЧПУ-02"))
                        .filter(a -> a.taskId().equals("черновая-токарка"))
                        .map(Assignment::plannedEnd)
                        .max(Instant::compareTo)
                        .orElseThrow();

        Instant firstFinishStart =
                AssignmentFilters.work(store.assignments()).stream()
                        .filter(a -> a.orderId().equals(ORDER))
                        .filter(a -> a.machineId().equals("ТОКАР-ЧПУ-03"))
                        .filter(a -> a.taskId().equals("чистовая-токарка"))
                        .map(Assignment::plannedStart)
                        .min(Instant::compareTo)
                        .orElseThrow();

        assertTrue(
                firstFinishStart.isBefore(lastRoughEnd),
                "чистовая на 03 должна стартовать раньше конца последней черновой на 02: finish="
                        + firstFinishStart
                        + " roughEnd="
                        + lastRoughEnd);
    }

    @Test
    void finishMillOnSecondFrez_startsBeforeLastRoughOnFirst() {
        Instant lastRoughEnd =
                AssignmentFilters.work(store.assignments()).stream()
                        .filter(a -> a.orderId().equals(ORDER))
                        .filter(a -> a.machineId().equals("ФРЕЗ-ЧПУ-01"))
                        .filter(a -> a.taskId().equals("черновая-фрезеровка"))
                        .map(Assignment::plannedEnd)
                        .max(Instant::compareTo)
                        .orElseThrow();

        Instant firstFinishStart =
                AssignmentFilters.work(store.assignments()).stream()
                        .filter(a -> a.orderId().equals(ORDER))
                        .filter(a -> a.machineId().equals("ФРЕЗ-ЧПУ-02"))
                        .filter(a -> a.taskId().equals("чистовая-фрезеровка"))
                        .map(Assignment::plannedStart)
                        .min(Instant::compareTo)
                        .orElseThrow();

        assertTrue(firstFinishStart.isBefore(lastRoughEnd));
    }

    @Test
    void roughTurn_onFirstTokar_finishOnSecond() {
        assertEquals(QTY, countRough("ТОКАР-ЧПУ-02", "черновая-токарка"));
        assertEquals(0, countRough("ТОКАР-ЧПУ-03", "черновая-токарка"));
        assertEquals(QTY, countRough("ТОКАР-ЧПУ-03", "чистовая-токарка"));
        assertEquals(0, countRough("ТОКАР-ЧПУ-02", "чистовая-токарка"));
    }

    @Test
    void roughMill_onFirstFrez_finishOnSecond() {
        assertEquals(QTY, countRough("ФРЕЗ-ЧПУ-01", "черновая-фрезеровка"));
        assertEquals(0, countRough("ФРЕЗ-ЧПУ-02", "черновая-фрезеровка"));
        assertEquals(QTY, countRough("ФРЕЗ-ЧПУ-02", "чистовая-фрезеровка"));
        assertEquals(0, countRough("ФРЕЗ-ЧПУ-01", "чистовая-фрезеровка"));
    }

    @Test
    void eachUnit_finishTurnAfterRoughEnd_sameUnitIndex() {
        List<Assignment> rough = AssignmentFilters.work(store.assignments()).stream()
                .filter(a -> a.orderId().equals(ORDER))
                .filter(a -> a.taskId().equals("черновая-токарка"))
                .toList();
        List<Assignment> finish = AssignmentFilters.work(store.assignments()).stream()
                .filter(a -> a.orderId().equals(ORDER))
                .filter(a -> a.taskId().equals("чистовая-токарка"))
                .toList();
        for (int unit = 0; unit < QTY; unit++) {
            int u = unit;
            Assignment r = rough.stream().filter(a -> a.unitIndex() == u).findFirst().orElseThrow();
            Assignment f = finish.stream().filter(a -> a.unitIndex() == u).findFirst().orElseThrow();
            assertTrue(
                    !f.plannedStart().isBefore(r.plannedEnd()),
                    () -> "чистовая #" + u + " не раньше конца черновой #" + u);
        }
    }

    @Test
    void grinding_startsAfterFinishTurnPerUnit_notBefore() {
        List<Assignment> finish = AssignmentFilters.work(store.assignments()).stream()
                .filter(a -> a.orderId().equals(ORDER))
                .filter(a -> a.taskId().equals("чистовая-токарка"))
                .toList();
        List<Assignment> grind = AssignmentFilters.work(store.assignments()).stream()
                .filter(a -> a.orderId().equals(ORDER))
                .filter(a -> a.taskId().equals("шлифование-сегментов"))
                .toList();
        for (int unit = 0; unit < QTY; unit++) {
            int u = unit;
            Assignment f = finish.stream().filter(a -> a.unitIndex() == u).findFirst().orElseThrow();
            Assignment g = grind.stream().filter(a -> a.unitIndex() == u).findFirst().orElseThrow();
            assertTrue(
                    !g.plannedStart().isBefore(f.plannedEnd()),
                    () -> "шлифование #" + u + " не раньше конца чистовой #" + u);
        }
        Instant firstGrind = grind.stream()
                .map(Assignment::plannedStart)
                .min(Instant::compareTo)
                .orElseThrow();
        Instant firstFinishEnd = finish.stream()
                .filter(a -> a.unitIndex() == 0)
                .map(Assignment::plannedEnd)
                .findFirst()
                .orElseThrow();
        assertTrue(
                !firstGrind.isBefore(firstFinishEnd),
                "первое шлифование не раньше конца чистовой шт.0");
    }

    @Test
    void singleTokar_noFinishBeforeAllRough() throws IOException {
        Path scheduleFile = tempDir.resolve("single-tokar.json");
        Files.copy(Path.of("data/schedule.json.example"), scheduleFile);
        ScheduleStore single = new JsonScheduleRepository(scheduleFile).loadOrCreate();
        SchedulerService svc =
                new SchedulerService(single, new JsonScheduleRepository(scheduleFile), new StoreCurrentTimeProvider(single));
        svc.addOrder(new OrderRequest(ORDER, List.of(new OrderPartRequest("вал-буровой", QTY))));

        Instant lastRough = AssignmentFilters.work(single.assignments()).stream()
                .filter(a -> a.taskId().equals("черновая-токарка"))
                .map(Assignment::plannedEnd)
                .max(Instant::compareTo)
                .orElseThrow();
        Instant firstFinish = AssignmentFilters.work(single.assignments()).stream()
                .filter(a -> a.taskId().equals("чистовая-токарка"))
                .map(Assignment::plannedStart)
                .min(Instant::compareTo)
                .orElseThrow();

        assertFalse(
                firstFinish.isBefore(lastRough),
                "один токарный: чистовая только после всей черновой");
    }

    private long countRough(String machineId, String taskId) {
        return AssignmentFilters.work(store.assignments()).stream()
                .filter(a -> a.orderId().equals(ORDER))
                .filter(a -> a.machineId().equals(machineId))
                .filter(a -> a.taskId().equals(taskId))
                .map(Assignment::unitIndex)
                .distinct()
                .count();
    }
}
