package scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import scheduler.engine.FactoryZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import scheduler.api.OrderPartRequest;
import scheduler.api.OrderRequest;
import scheduler.engine.BatchOverlap;
import scheduler.engine.OrderPriorities;
import scheduler.engine.ScheduleMetrics;
import scheduler.model.Order;
import scheduler.model.Part;
import scheduler.model.Capability;
import scheduler.model.MachineStatus;
import scheduler.model.SetupIntervals;
import scheduler.service.AddOrderResult;
import scheduler.service.SchedulerService;
import scheduler.service.SchedulingException;
import scheduler.store.JsonScheduleRepository;
import scheduler.store.PartDefinition;
import scheduler.store.ScheduleStore;
import scheduler.model.Task;
import scheduler.time.StoreCurrentTimeProvider;

class GreedySchedulerTest {
    @TempDir
    Path tempDir;

    private SchedulerService service;
    private ScheduleStore store;
    private Instant factoryStart;

    @BeforeEach
    void setUp() throws IOException {
        factoryStart = Instant.parse("2026-05-22T08:00:00Z");
        store = ScheduleStore.empty(factoryStart, true, factoryStart);
        seedPartCatalog(store);
        JsonScheduleRepository repository = new JsonScheduleRepository(tempDir.resolve("schedule.json"));
        service = new SchedulerService(store, repository, new StoreCurrentTimeProvider(store));
    }

    private static void seedPartCatalog(ScheduleStore store) {
        store.setPartDefinition(
                "P1",
                new PartDefinition(
                        10,
                        List.of(
                                new Task("T1", 0, Duration.ofMinutes(60), Capability.MILLING),
                                new Task("T2", 1, Duration.ofMinutes(30), Capability.TURNING))));
        store.setPartDefinition(
                "P-high",
                new PartDefinition(10, List.of(new Task("T1", 0, Duration.ofMinutes(100), Capability.MILLING))));
        store.setPartDefinition(
                "P-low",
                new PartDefinition(5, List.of(new Task("T2", 0, Duration.ofMinutes(40), Capability.TURNING))));
        store.setPartDefinition(
                "P-slow",
                new PartDefinition(10, List.of(new Task("T1", 0, Duration.ofMinutes(80), Capability.MILLING))));
        store.setPartDefinition(
                "P-fast",
                new PartDefinition(5, List.of(new Task("T2", 0, Duration.ofMinutes(20), Capability.TURNING))));
        store.setPartDefinition(
                "P2",
                new PartDefinition(5, List.of(new Task("T2", 0, Duration.ofMinutes(30), Capability.MILLING))));
        store.setPartDefinition(
                "P-overlap",
                new PartDefinition(
                        8,
                        List.of(
                                new Task("T-mill", 0, Duration.ofMinutes(20), Capability.MILLING),
                                new Task("T-turn", 1, Duration.ofMinutes(10), Capability.TURNING))));
    }

    private static OrderPartRequest line(String partId) {
        return new OrderPartRequest(partId, 1);
    }

    private static OrderPartRequest line(String partId, int quantity) {
        return new OrderPartRequest(partId, quantity);
    }

    @Test
    void addOrder_operationTooLongForShiftTail_startsNextWorkDay() throws IOException {
        store.setPartDefinition(
                "P-tail",
                new PartDefinition(10, List.of(new Task("T1", 0, Duration.ofMinutes(70), Capability.MILLING))));
        Instant lateFriday =
                ZonedDateTime.of(2026, 5, 22, 19, 30, 0, 0, FactoryZone.ZONE).toInstant();
        store.findMachine("ФРЕЗ-ЧПУ-01").setAvailableAt(lateFriday);
        service.setSimulationTime(lateFriday);

        AddOrderResult result = service.addOrder(new OrderRequest("O1", List.of(line("P-tail"))));

        Instant expectedMonday =
                ZonedDateTime.of(2026, 5, 25, 8, 30, 0, 0, FactoryZone.ZONE).toInstant();
        Instant workStart = result.assignmentsForOrder().stream()
                .filter(a -> !SetupIntervals.isSetup(a.taskId()))
                .map(a -> a.plannedStart())
                .findFirst()
                .orElseThrow();
        assertEquals(expectedMonday, workStart);
    }

    @Test
    void addOrder_withoutOrderId_assignsNextNumber() throws IOException {
        store.addOrder(new scheduler.model.Order(
                "З-2026-0005",
                factoryStart,
                List.of(store.createPart("P1", 1)),
                1));
        AddOrderResult result = service.addOrder(new OrderRequest(null, List.of(line("P1"))));

        assertEquals("З-2026-0006", result.orderId());
    }

    @Test
    void addOrder_singlePart_returnsReadyAt() throws IOException {
        AddOrderResult result = service.addOrder(new OrderRequest("O1", List.of(line("P1"))));

        assertEquals("O1", result.orderId());
        assertEquals(4, result.assignmentsForOrder().size());
        assertEquals(factoryStart.plus(Duration.ofMinutes(150)), result.readyAt());
        assertTrue(Files.exists(tempDir.resolve("schedule.json")));
    }

    @Test
    void addOrder_unknownPartId_rejected() {
        assertThrows(
                SchedulingException.class,
                () -> service.addOrder(new OrderRequest("O1", List.of(line("UNKNOWN")))));
    }

    @Test
    void addOrder_twoParts_parallelOnDifferentMachines() throws IOException {
        AddOrderResult result = service.addOrder(new OrderRequest("O1", List.of(line("P-high"), line("P-low"))));

        assertEquals(factoryStart.plus(Duration.ofMinutes(130)), result.readyAt());
        assertEquals(4, result.assignmentsForOrder().size());
        Instant highEnd = result.assignmentsForOrder().stream()
                .filter(a -> a.partId().equals("P-high"))
                .map(a -> a.plannedEnd())
                .max(Instant::compareTo)
                .orElseThrow();
        Instant lowStart = result.assignmentsForOrder().stream()
                .filter(a -> a.partId().equals("P-low"))
                .map(a -> a.plannedStart())
                .min(Instant::compareTo)
                .orElseThrow();
        assertTrue(lowStart.isBefore(highEnd));
    }

    @Test
    void addOrder_quantity_twoUnits_schedulesBothBeforeNextOperation() throws IOException {
        AddOrderResult result = service.addOrder(new OrderRequest("O1", List.of(line("P1", 2))));

        long t1Count = result.assignmentsForOrder().stream()
                .filter(a -> a.partId().equals("P1") && a.taskId().equals("T1"))
                .count();
        assertEquals(2, t1Count);
        for (int unit = 0; unit < 2; unit++) {
            int u = unit;
            Instant t1End = result.assignmentsForOrder().stream()
                    .filter(a -> a.taskId().equals("T1") && a.unitIndex() == u)
                    .map(a -> a.plannedEnd())
                    .findFirst()
                    .orElseThrow();
            Instant t2Start = result.assignmentsForOrder().stream()
                    .filter(a -> a.taskId().equals("T2") && a.unitIndex() == u)
                    .map(a -> a.plannedStart())
                    .findFirst()
                    .orElseThrow();
            assertTrue(
                    !t2Start.isBefore(t1End),
                    () -> "T2 шт." + u + " не раньше конца T1 шт." + u);
        }
    }

    @Test
    void addOrder_noOverlappingIntervalsOnSameMachine() throws IOException {
        AddOrderResult result = service.addOrder(new OrderRequest("O1", List.of(line("P1"))));

        for (String machineId : result.assignmentsForOrder().stream()
                .map(a -> a.machineId())
                .distinct()
                .toList()) {
            var onMachine = result.assignmentsForOrder().stream()
                    .filter(a -> a.machineId().equals(machineId))
                    .sorted(java.util.Comparator.comparing(a -> a.plannedStart()))
                    .toList();
            for (int i = 1; i < onMachine.size(); i++) {
                var prev = onMachine.get(i - 1);
                var cur = onMachine.get(i);
                assertTrue(
                        !prev.plannedEnd().isAfter(cur.plannedStart()),
                        () -> "Overlap on "
                                + machineId
                                + ": "
                                + prev.taskId()
                                + " ends "
                                + prev.plannedEnd()
                                + " after "
                                + cur.taskId()
                                + " starts "
                                + cur.plannedStart());
            }
            result.assignmentsForOrder().stream()
                    .filter(a -> a.machineId().equals(machineId))
                    .filter(a -> SetupIntervals.isSetup(a.taskId()))
                    .forEach(setup -> {
                        var work = onMachine.stream()
                                .filter(a -> !SetupIntervals.isSetup(a.taskId()))
                                .filter(a -> a.unitIndex() == setup.unitIndex())
                                .filter(a -> a.plannedStart().compareTo(setup.plannedStart()) >= 0)
                                .findFirst()
                                .orElseThrow();
                        assertTrue(
                                !setup.plannedEnd().isAfter(work.plannedStart()),
                                "Setup must end before or when work starts on same machine");
                    });
        }
    }

    @Test
    void addOrder_secondOpStartsBeforeBatchEndsOnPrevMachine() throws IOException {
        store.setOverlapBatchesEnabled(true);
        AddOrderResult result = service.addOrder(new OrderRequest("O2", List.of(line("P-overlap", 10))));

        Instant lastMillEnd = result.assignmentsForOrder().stream()
                .filter(a -> a.taskId().equals("T-mill"))
                .map(a -> a.plannedEnd())
                .max(Instant::compareTo)
                .orElseThrow();
        Instant firstTurnStart = result.assignmentsForOrder().stream()
                .filter(a -> a.taskId().equals("T-turn"))
                .map(a -> a.plannedStart())
                .min(Instant::compareTo)
                .orElseThrow();
        assertTrue(firstTurnStart.isBefore(lastMillEnd));

        Order order = store.findOrder("O2").orElseThrow();
        Part overlapPart = store.createPart("P-overlap", 10);
        Instant expectedMin =
                BatchOverlap.earliestPackageStartForContinuousFeed(
                        order,
                        overlapPart,
                        1,
                        result.assignmentsForOrder(),
                        factoryStart);
        assertTrue(
                !firstTurnStart.isBefore(expectedMin),
                "Старт токарки не раньше минимума по штукам/непрерывности");
        Instant turn0Start = result.assignmentsForOrder().stream()
                .filter(a -> a.taskId().equals("T-turn") && a.unitIndex() == 0)
                .map(a -> a.plannedStart())
                .findFirst()
                .orElseThrow();
        Instant mill0End = result.assignmentsForOrder().stream()
                .filter(a -> a.taskId().equals("T-mill") && a.unitIndex() == 0)
                .map(a -> a.plannedEnd())
                .findFirst()
                .orElseThrow();
        assertTrue(!turn0Start.isBefore(mill0End), "токарка шт.0 не раньше конца фрезы шт.0");
    }

    @Test
    void addOrder_setupEndsWhenWorkStarts() throws IOException {
        AddOrderResult result = service.addOrder(new OrderRequest("O3", List.of(line("P-overlap", 2))));

        result.assignmentsForOrder().stream()
                .filter(a -> SetupIntervals.isSetup(a.taskId()))
                .forEach(setup -> {
                    Instant nextWorkStart = result.assignmentsForOrder().stream()
                            .filter(a -> !SetupIntervals.isSetup(a.taskId()))
                            .filter(a -> a.machineId().equals(setup.machineId()))
                            .filter(a -> a.unitIndex() == setup.unitIndex())
                            .filter(a -> a.plannedStart().compareTo(setup.plannedEnd()) >= 0)
                            .map(a -> a.plannedStart())
                            .min(Instant::compareTo)
                            .orElseThrow();
                    assertEquals(0, setup.plannedEnd().compareTo(nextWorkStart));
                });
    }

    @Test
    void addOrder_quantity_batchByOperation_thenUnitsInSequence() throws IOException {
        AddOrderResult result = service.addOrder(new OrderRequest("O1", List.of(line("P1", 3))));

        long workAssignments = result.assignmentsForOrder().stream()
                .filter(a -> !SetupIntervals.isSetup(a.taskId()))
                .count();
        assertEquals(6, workAssignments);
        assertEquals(2, result.assignmentsForOrder().stream()
                .filter(a -> a.partId().equals("P1") && a.unitIndex() == 0 && !SetupIntervals.isSetup(a.taskId()))
                .count());
        assertEquals(2, result.assignmentsForOrder().stream()
                .filter(a -> a.partId().equals("P1") && a.unitIndex() == 2 && !SetupIntervals.isSetup(a.taskId()))
                .count());
        assertEquals(
                result.assignmentsForOrder().stream()
                        .map(a -> a.plannedEnd())
                        .max(Instant::compareTo)
                        .orElseThrow(),
                result.readyAt());

        for (int unit = 0; unit < 3; unit++) {
            int u = unit;
            Instant t1End = result.assignmentsForOrder().stream()
                    .filter(a -> a.partId().equals("P1") && a.taskId().equals("T1") && a.unitIndex() == u)
                    .map(a -> a.plannedEnd())
                    .findFirst()
                    .orElseThrow();
            Instant t2Start = result.assignmentsForOrder().stream()
                    .filter(a -> a.partId().equals("P1") && a.taskId().equals("T2") && a.unitIndex() == u)
                    .map(a -> a.plannedStart())
                    .findFirst()
                    .orElseThrow();
            assertTrue(
                    !t2Start.isBefore(t1End),
                    () -> "T2 шт." + u + " не раньше конца T1 шт." + u);
        }
    }

    @Test
    void addOrder_priorityDerivedFromCreatedAt() throws IOException {
        service.addOrder(new OrderRequest("O1", List.of(line("P1"))));
        Instant later = factoryStart.plus(Duration.ofHours(1));
        service.setSimulationTime(later);
        service.addOrder(new OrderRequest("O2", List.of(line("P2"))));

        int p1 = store.orders().stream()
                .filter(o -> o.orderId().equals("O1"))
                .findFirst()
                .orElseThrow()
                .priority();
        int p2 = store.orders().stream()
                .filter(o -> o.orderId().equals("O2"))
                .findFirst()
                .orElseThrow()
                .priority();
        assertTrue(p1 > p2, "более ранний заказ должен иметь больший приоритет");
        assertEquals(OrderPriorities.fromCreatedAt(factoryStart), p1);
    }

    @Test
    void addOrder_createdAtIsCurrentSimulationTime() throws IOException {
        service.addOrder(new OrderRequest("O1", List.of(line("P1"))));

        Instant later = factoryStart.plus(Duration.ofHours(2));
        service.setSimulationTime(later);

        service.addOrder(new OrderRequest("O2", List.of(line("P2"))));

        Instant o2Created = store.orders().stream()
                .filter(o -> o.orderId().equals("O2"))
                .findFirst()
                .orElseThrow()
                .createdAt();
        assertEquals(later, o2Created);
    }

    @Test
    void setSimulationTime_rejectsBackward() throws IOException {
        Instant later = factoryStart.plus(Duration.ofHours(2));
        service.setSimulationTime(later);

        Instant earlier = factoryStart.plus(Duration.ofHours(1));
        SchedulingException ex =
                assertThrows(SchedulingException.class, () -> service.setSimulationTime(earlier));
        assertTrue(ex.getMessage().contains("only move forward"));
    }

    @Test
    void simulationTime_advanceThenNewOrderUsesNewTimeline() throws IOException {
        service.addOrder(new OrderRequest("O1", List.of(line("P1"))));

        Instant later = factoryStart.plus(Duration.ofHours(2));
        service.setSimulationTime(later);

        AddOrderResult r2 = service.addOrder(new OrderRequest("O2", List.of(line("P2"))));

        assertEquals(later.plus(Duration.ofMinutes(60)), r2.readyAt());
        assertTrue(r2.assignmentsForOrder().getFirst().plannedStart().compareTo(later) >= 0);
    }

    @Test
    void machineDown_blocksScheduling() throws IOException {
        service.setMachineStatus("ТОКАР-ЧПУ-02", MachineStatus.DOWN);

        assertThrows(SchedulingException.class, () -> service.addOrder(new OrderRequest("O1", List.of(line("P1")))));
    }

    @Test
    void scheduleView_containsSlackForFasterPartOnParallelMachine() throws IOException {
        service.addOrder(new OrderRequest("O1", List.of(line("P-slow"), line("P-fast"))));

        Instant orderReady = ScheduleMetrics.readyAt("O1", store.assignments());
        Instant partFastReady = ScheduleMetrics.partReadyAt("O1", "P-fast", store.assignments());

        assertEquals(Duration.ofMinutes(60), Duration.between(partFastReady, orderReady));
        assertTrue(partFastReady.isBefore(orderReady));
    }

    @Test
    void tasksWithinPartPreserveSequence() throws IOException {
        AddOrderResult result = service.addOrder(new OrderRequest("O1", List.of(line("P1"))));

        var assignments = result.assignmentsForOrder().stream()
                .sorted(java.util.Comparator.comparingInt(a -> a.sequence()))
                .toList();
        assertTrue(assignments.get(0).plannedEnd().compareTo(assignments.get(1).plannedStart()) <= 0);
    }
}
