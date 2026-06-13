package scheduler.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import scheduler.model.Assignment;
import scheduler.model.Capability;
import scheduler.model.Machine;
import scheduler.model.MachineStatus;
import scheduler.model.Order;
import scheduler.model.Part;
import scheduler.model.Task;
import scheduler.store.ScheduleStore;

class BatchOverlapTest {
    private ScheduleStore store;
    private final Task roughTurn =
            new Task("черновая-токарка", 0, Duration.ofMinutes(70), Capability.TURNING);
    private final Task finishTurn =
            new Task("чистовая-токарка", 1, Duration.ofMinutes(45), Capability.TURNING);
    private final Task roughMill =
            new Task("черновая-фрезеровка", 0, Duration.ofMinutes(90), Capability.MILLING);
    private final Task boring =
            new Task("расточивание-отверстий", 1, Duration.ofMinutes(120), Capability.DEEP_BORING);
    private final Task finishMill =
            new Task("чистовая-фрезеровка", 2, Duration.ofMinutes(60), Capability.MILLING);

    @BeforeEach
    void setUp() {
        Instant factory = Instant.parse("2026-05-22T08:00:00Z");
        store = ScheduleStore.empty(factory, true, factory);
        store.setOverlapBatchesEnabled(true);
    }

    @Test
    void allowsParallelBatchesBetween_falseWhenOverlapDisabled() {
        store.setOverlapBatchesEnabled(false);
        assertFalse(BatchOverlap.allowsParallelBatchesBetween(store, roughMill, boring));
        store.setOverlapBatchesEnabled(true);
    }

    @Test
    void allowsParallelBatchesBetween_falseWhenSingleSharedMachine() {
        assertFalse(BatchOverlap.allowsParallelBatchesBetween(store, roughTurn, finishTurn));
        assertFalse(BatchOverlap.allowsParallelBatchesBetween(store, roughMill, finishMill));
    }

    @Test
    void allowsParallelBatchesBetween_trueWhenDifferentCapabilities() {
        assertTrue(BatchOverlap.allowsParallelBatchesBetween(store, roughMill, boring));
    }

    @Test
    void allowsParallelBatchesBetween_trueWhenTwoSharedMachines() {
        Instant factory = Instant.parse("2026-05-22T08:00:00Z");
        ScheduleStore twoTokars = ScheduleStore.empty(factory, true, factory);
        twoTokars.setOverlapBatchesEnabled(true);
        twoTokars.machines()
                .add(new Machine(
                        "ТОКАР-ЧПУ-03",
                        "cnc",
                        Set.of(Capability.TURNING),
                        factory,
                        MachineStatus.IDLE));
        assertEquals(2, BatchOverlap.sharedOperationalMachines(twoTokars, Capability.TURNING, Capability.TURNING)
                .size());
        assertTrue(BatchOverlap.allowsParallelBatchesBetween(twoTokars, roughTurn, finishTurn));
    }

    @Test
    void earliestBatchWorkStart_formula_Q12_pPrev70_pCur45() {
        Instant factory = Instant.parse("2026-05-22T08:00:00Z");
        Part part = new Part("вал-буровой", 12, List.of(roughTurn, finishTurn));
        Order order = new Order("O1", factory, List.of(part), 1);
        List<Assignment> assignments = List.of(Assignment.planned(
                "r0",
                "O1",
                "вал-буровой",
                0,
                "черновая-токарка",
                0,
                "ТОКАР-ЧПУ-02",
                factory,
                factory.plus(Duration.ofMinutes(70))));

        Instant anchor = BatchOverlap.batchAnchorStart(order, part, 0, assignments, factory);
        Instant earliest = BatchOverlap.earliestBatchWorkStart(order, part, 1, assignments, factory);

        assertEquals(anchor.plus(Duration.ofMinutes(345)), earliest);
    }

    @Test
    void overlapMode_perUnit_forFinishTurnToGrinding() {
        Task grind = new Task("шлифование-сегментов", 2, Duration.ofMinutes(50), Capability.GRINDING);
        assertEquals(
                BatchOverlap.OverlapMode.PER_UNIT,
                BatchOverlap.overlapMode(store, finishTurn, grind));
        assertFalse(BatchOverlap.usesPipelineBatchStart(finishTurn, grind));
    }

    @Test
    void overlapMode_pipeline_forMillToBoring() {
        assertEquals(
                BatchOverlap.OverlapMode.PIPELINE,
                BatchOverlap.overlapMode(store, roughMill, boring));
    }

    @Test
    void sharedOperationalMachines_intersectionOnly() {
        assertEquals(1, BatchOverlap.sharedOperationalMachines(store, Capability.MILLING, Capability.MILLING)
                .size());
        assertTrue(BatchOverlap.sharedOperationalMachines(store, Capability.MILLING, Capability.DEEP_BORING)
                .isEmpty());
    }
}
