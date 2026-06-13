package scheduler.engine.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import scheduler.engine.policy.FactoryZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import scheduler.api.dto.OrderPartRequest;
import scheduler.api.dto.OrderRequest;
import scheduler.engine.policy.OrderPriorities;
import scheduler.engine.metrics.OrderProgress;
import scheduler.model.order.Order;
import scheduler.model.order.Part;
import scheduler.model.machine.Capability;
import scheduler.model.schedule.SetupIntervals;
import scheduler.service.AddOrderResult;
import scheduler.service.SchedulerService;
import scheduler.service.SchedulingException;
import scheduler.store.InMemoryPlanningRepository;
import scheduler.store.TestFactoryFixtures;
import scheduler.store.core.PartDefinition;
import scheduler.model.order.Task;
import scheduler.time.FixedTimeProvider;

class GreedySchedulerTest {
    private InMemoryPlanningRepository repository;
    private SchedulerService service;
    private Instant factoryStart;

    @BeforeEach
    void setUp() {
        factoryStart = Instant.parse("2026-05-22T08:00:00Z");
        repository = new InMemoryPlanningRepository(factoryStart);
        TestFactoryFixtures.seedGreedySchedulerCatalog(repository);
        service = new SchedulerService(repository, repository, new FixedTimeProvider(factoryStart));
    }

    private static OrderPartRequest line(String partId) {
        return new OrderPartRequest(partId, 1);
    }

    private static OrderPartRequest line(String partId, int quantity) {
        return new OrderPartRequest(partId, quantity);
    }

    @Test
    void addOrder_machineBusyUntilLate_startsAfterAvailability() throws IOException {
        repository.putPartDefinition(
                "P-tail",
                new PartDefinition(10, List.of(new Task("T1", 0, Duration.ofMinutes(70), Capability.MILLING))));
        Instant lateFriday =
                ZonedDateTime.of(2026, 5, 22, 19, 30, 0, 0, FactoryZone.ZONE).toInstant();
        repository.machine("ФРЕЗ-ЧПУ-01").setAvailableAt(lateFriday);

        AddOrderResult result = service.addOrder(new OrderRequest("O1", List.of(line("P-tail"))));

        Instant workStart = result.assignmentsForOrder().stream()
                .filter(a -> !SetupIntervals.isSetup(a.taskId()))
                .map(a -> a.plannedStart())
                .findFirst()
                .orElseThrow();
        assertTrue(!workStart.isBefore(lateFriday));
    }

    @Test
    void addOrder_withoutOrderId_assignsNextNumber() throws IOException {
        repository.addOrder(new Order(
                "З-2026-0005",
                factoryStart,
                List.of(new Part("P1", 1, repository.partTasks("P1"))),
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
    void scheduleView_containsSlackForFasterPartOnParallelMachine() throws IOException {
        AddOrderResult result = service.addOrder(new OrderRequest("O1", List.of(line("P-slow"), line("P-fast"))));

        Instant orderReady = OrderProgress.readyAt("O1", result.assignmentsForOrder());
        Instant partFastReady = OrderProgress.partReadyAt("O1", "P-fast", result.assignmentsForOrder());

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
