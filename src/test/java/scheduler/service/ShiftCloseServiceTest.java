package scheduler.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import scheduler.api.MachineIdleBlockRequest;
import scheduler.api.ShiftCloseRequest;
import scheduler.api.ShiftOperationFactRequest;
import scheduler.engine.FactoryZone;
import scheduler.model.AssignmentStatus;
import scheduler.model.Capability;
import scheduler.model.Task;
import scheduler.store.JsonScheduleRepository;
import scheduler.store.PartDefinition;
import scheduler.store.ScheduleStore;
import scheduler.time.StoreCurrentTimeProvider;

class ShiftCloseServiceTest {
    @TempDir
    Path tempDir;

    private SchedulerService service;
    private ScheduleStore store;
    private Instant factoryStart;

    @BeforeEach
    void setUp() throws IOException {
        factoryStart = Instant.parse("2026-05-22T08:00:00Z");
        store = ScheduleStore.empty(factoryStart, true, factoryStart);
        store.setPartDefinition(
                "P1",
                new PartDefinition(
                        10,
                        List.of(
                                new Task("T1", 0, Duration.ofMinutes(60), Capability.MILLING),
                                new Task("T2", 1, Duration.ofMinutes(30), Capability.TURNING))));
        JsonScheduleRepository repository = new JsonScheduleRepository(tempDir.resolve("schedule.json"));
        service = new SchedulerService(store, repository, new StoreCurrentTimeProvider(store));
        service.addOrder(new scheduler.api.OrderRequest("O1", List.of(new scheduler.api.OrderPartRequest("P1", 1))));
    }

    @Test
    void closeShift_completedFaster_replansNextOperationEarlier() throws IOException {
        Instant actualEnd = factoryStart.plus(Duration.ofMinutes(30));
        ShiftCloseResult result = service.closeShift(new ShiftCloseRequest(
                factoryStart.plus(Duration.ofHours(8)),
                List.of(new ShiftOperationFactRequest(
                        "O1", "P1", 0, "T1", true, null, actualEnd)),
                List.of()));

        assertEquals(1, result.completedCount());
        var t2 = store.assignments().stream()
                .filter(a -> a.taskId().equals("T2") && a.isPlanned())
                .findFirst()
                .orElseThrow();
        assertTrue(t2.plannedStart().compareTo(actualEnd) >= 0);
        assertTrue(Files.exists(tempDir.resolve("schedule.json")));
    }

    @Test
    void closeShift_notCompleted_reschedulesOperation() throws IOException {
        Instant shiftEnd = factoryStart.plus(Duration.ofHours(8));
        ShiftCloseResult result = service.closeShift(new ShiftCloseRequest(
                shiftEnd,
                List.of(new ShiftOperationFactRequest("O1", "P1", 0, "T1", false, null, null)),
                List.of()));

        assertEquals(2, result.cancelledCount());
        assertTrue(store.assignments().stream()
                .anyMatch(a -> a.taskId().equals("T1") && a.isPlanned()));
    }

    @Test
    void closeShift_idleBlock_pushesWorkAfterBlock() throws IOException {
        Instant blockFrom = factoryStart.plus(Duration.ofHours(2));
        Instant blockTo = factoryStart.plus(Duration.ofHours(4));
        service.closeShift(new ShiftCloseRequest(
                factoryStart.plus(Duration.ofHours(8)),
                List.of(new ShiftOperationFactRequest("O1", "P1", 0, "T1", false, null, null)),
                List.of(new MachineIdleBlockRequest("ФРЕЗ-ЧПУ-01", blockFrom, blockTo, "простой"))));

        var t1 = store.assignments().stream()
                .filter(a -> a.taskId().equals("T1") && a.isPlanned())
                .findFirst()
                .orElseThrow();
        assertTrue(!t1.plannedStart().isBefore(blockTo));
        assertEquals(1, store.machineBlocks().size());
    }

    @Test
    void closeShift_completedSlower_delaysTail() throws IOException {
        Instant slowEnd = factoryStart.plus(Duration.ofHours(3));
        service.closeShift(new ShiftCloseRequest(
                slowEnd,
                List.of(new ShiftOperationFactRequest("O1", "P1", 0, "T1", true, null, slowEnd)),
                List.of()));

        var t2 = store.assignments().stream()
                .filter(a -> a.taskId().equals("T2") && a.isPlanned())
                .findFirst()
                .orElseThrow();
        assertTrue(t2.plannedStart().compareTo(slowEnd) >= 0);
    }
}
