package scheduler.engine.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import scheduler.model.schedule.Assignment;
import scheduler.model.schedule.AssignmentStatus;
import scheduler.model.machine.Machine;
import scheduler.model.machine.MachineGroupDefaults;
import scheduler.store.core.ScheduleStore;

class SetupPlannerTest {
    private ScheduleStore store;
    private Machine milling;
    private Machine turning;

    @BeforeEach
    void setUp() {
        Instant t = Instant.parse("2026-05-22T08:00:00Z");
        store = ScheduleStore.empty(t);
        milling = store.machines().stream()
                .filter(m -> m.machineId().equals("ФРЕЗ-ЧПУ-01"))
                .findFirst()
                .orElseThrow();
        turning = store.machines().stream()
                .filter(m -> m.machineId().equals("ТОКАР-ЧПУ-02"))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void setupBeforeTask_firstWorkOnMachine_usesGroupDefault() {
        Duration setup = SetupPlanner.setupBeforeTask(milling, "корпус-бура", "черновая-фрезеровка", store);
        assertEquals(MachineGroupDefaults.setupDuration("cnc"), setup);
    }

    @Test
    void setupBeforeTask_samePartAndTaskAgain_noSetup() {
        store.addAssignment(workAssignment("корпус-бура", "черновая-фрезеровка", milling.machineId()));
        Duration setup = SetupPlanner.setupBeforeTask(milling, "корпус-бура", "черновая-фрезеровка", store);
        assertTrue(setup.isZero());
    }

    @Test
    void setupBeforeTask_samePartDifferentTask_needsSetup() {
        store.addAssignment(workAssignment("вал-буровой", "черновая-токарка", turning.machineId()));
        Duration setup = SetupPlanner.setupBeforeTask(turning, "вал-буровой", "чистовая-токарка", store);
        assertEquals(Duration.ofMinutes(30), setup);
    }

    @Test
    void setupBeforeTask_differentPart_usesGroupDefault() {
        store.addAssignment(workAssignment("корпус-бура", "черновая-фрезеровка", milling.machineId()));
        Duration setup = SetupPlanner.setupBeforeTask(milling, "вал-буровой", "черновая-фрезеровка", store);
        assertEquals(Duration.ofMinutes(30), setup);
    }

    @Test
    void setupBeforeTask_ignoresCancelledLaterDifferentTaskOnMachine() {
        Instant t = Instant.parse("2026-05-22T08:00:00Z");
        store.addAssignment(Assignment.planned(
                "done",
                "Z-1",
                "вал-буровой",
                1,
                "черновая-токарка",
                0,
                turning.machineId(),
                t,
                t.plus(Duration.ofMinutes(30))));
        store.addAssignment(new Assignment(
                "cancelled",
                "Z-1",
                "вал-буровой",
                5,
                "чистовая-токарка",
                1,
                turning.machineId(),
                t.plus(Duration.ofHours(4)),
                t.plus(Duration.ofHours(5)),
                AssignmentStatus.CANCELLED,
                null,
                null));

        Duration setup = SetupPlanner.setupBeforeTask(turning, "вал-буровой", "черновая-токарка", store);
        assertTrue(setup.isZero(), "отменённая чистовая не должна требовать переналадку перед продолжением черновой");
    }

    private static Assignment workAssignment(String partId, String taskId, String machineId) {
        Instant t = Instant.parse("2026-05-22T08:00:00Z");
        return Assignment.planned(
                "a1",
                "Z-1",
                partId,
                0,
                taskId,
                0,
                machineId,
                t,
                t.plus(Duration.ofMinutes(10)));
    }
}
