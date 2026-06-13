package scheduler.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import scheduler.api.ShiftContextView;
import scheduler.engine.FactoryZone;
import scheduler.engine.ShiftCalendar;
import scheduler.model.Capability;
import scheduler.model.Task;
import scheduler.store.PartDefinition;
import scheduler.store.ScheduleStore;
import scheduler.time.StoreCurrentTimeProvider;

class ShiftContextServiceTest {
    private ScheduleStore store;
    private ShiftContextService contextService;

    @BeforeEach
    void setUp() {
        Instant factoryStart = Instant.parse("2026-05-22T05:00:00Z");
        store = ScheduleStore.empty(factoryStart, true, factoryStart);
        store.setPartDefinition(
                "P1",
                new PartDefinition(
                        10,
                        List.of(new Task("T1", 0, Duration.ofMinutes(60), Capability.MILLING))));
        contextService = new ShiftContextService(store, new StoreCurrentTimeProvider(store));
    }

    @Test
    void build_pendingShiftWhenPreviousShiftEndedButNotClosed() {
        var group = store.findMachineGroup("cnc");
        Instant firstShiftEnd = ShiftCalendar.shiftWindowContaining(
                        Instant.parse("2026-05-22T06:00:00Z"), group, FactoryZone.ZONE)
                .orElseThrow()
                .end();
        store.setSimulationCurrentTime(firstShiftEnd.plusSeconds(60));
        contextService = new ShiftContextService(store, new StoreCurrentTimeProvider(store));

        ShiftContextView ctx = contextService.build();

        assertTrue(ctx.stale());
        assertTrue(ctx.pendingShiftCount() >= 1);
        assertNotNull(ctx.activeShift());
        assertTrue(ctx.activeShift().overdue());
    }

    @Test
    void build_notStaleAfterLastClosedMatchesNow() {
        Instant now = Instant.parse("2026-05-22T06:00:00Z");
        for (String groupId : store.machineGroups().keySet()) {
            var group = store.findMachineGroup(groupId);
            ShiftCalendar.shiftWindowContaining(now, group, FactoryZone.ZONE)
                    .ifPresent(w -> store.setLastClosedShiftEnd(groupId, w.end()));
        }
        store.setSimulationCurrentTime(now.minusSeconds(30));
        contextService = new ShiftContextService(store, new StoreCurrentTimeProvider(store));

        ShiftContextView ctx = contextService.build();

        assertFalse(ctx.stale());
        assertEquals(0, ctx.pendingShiftCount());
    }

    @Test
    void build_aggregatesPlannedCountsByMachineAndTask() {
        Instant shiftStart = Instant.parse("2026-05-22T05:00:00Z");
        Instant shiftEnd = Instant.parse("2026-05-22T11:00:00Z");
        store.addOrder(new scheduler.model.Order(
                "O1",
                shiftStart,
                List.of(store.createPart("P1", 2)),
                100));
        store.addAssignment(scheduler.model.Assignment.planned(
                "a1", "O1", "P1", 0, "T1", 0, "ФРЕЗ-ЧПУ-01", shiftStart, shiftEnd));
        store.addAssignment(scheduler.model.Assignment.planned(
                "a2", "O1", "P1", 1, "T1", 0, "ФРЕЗ-ЧПУ-01", shiftStart, shiftEnd));
        store.setSimulationCurrentTime(shiftStart.plusSeconds(60));
        contextService = new ShiftContextService(store, new StoreCurrentTimeProvider(store));

        ShiftContextView ctx = contextService.build();
        assertNotNull(ctx.activeShift());
        int planned = ctx.activeShift().machines().stream()
                .flatMap(m -> m.operations().stream())
                .mapToInt(o -> o.plannedCount())
                .sum();
        assertEquals(2, planned);
    }
}
