package scheduler.engine.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import scheduler.model.schedule.Assignment;
import scheduler.model.schedule.AssignmentStatus;
import scheduler.model.machine.Machine;
import scheduler.model.machine.MachineGroupDefaults;
import scheduler.store.InMemoryPlanningRepository;

class SetupPlannerTest {
    private InMemoryPlanningRepository repo;
    private Machine milling;
    private Machine turning;

    @BeforeEach
    void setUp() {
        Instant t = Instant.parse("2026-05-22T08:00:00Z");
        repo = new InMemoryPlanningRepository(t);
        milling = repo.machine("ФРЕЗ-ЧПУ-01");
        turning = repo.machine("ТОКАР-ЧПУ-02");
    }

    @Test
    void setupBeforeTask_firstWorkOnMachine_usesGroupDefault() throws IOException {
        Duration setup = SetupPlanner.setupBeforeTask(milling, "корпус-бура", "черновая-фрезеровка", repo);
        assertEquals(MachineGroupDefaults.setupDuration("cnc"), setup);
    }

    @Test
    void setupBeforeTask_samePartAndTaskAgain_noSetup() throws IOException {
        repo.addAssignment(workAssignment("корпус-бура", "черновая-фрезеровка", milling.machineId()));
        Duration setup = SetupPlanner.setupBeforeTask(milling, "корпус-бура", "черновая-фрезеровка", repo);
        assertTrue(setup.isZero());
    }

    @Test
    void setupBeforeTask_samePartDifferentTask_needsSetup() throws IOException {
        repo.addAssignment(workAssignment("вал-буровой", "черновая-токарка", turning.machineId()));
        Duration setup = SetupPlanner.setupBeforeTask(turning, "вал-буровой", "чистовая-токарка", repo);
        assertEquals(Duration.ofMinutes(30), setup);
    }

    @Test
    void setupBeforeTask_differentPart_usesGroupDefault() throws IOException {
        repo.addAssignment(workAssignment("корпус-бура", "черновая-фрезеровка", milling.machineId()));
        Duration setup = SetupPlanner.setupBeforeTask(milling, "вал-буровой", "черновая-фрезеровка", repo);
        assertEquals(Duration.ofMinutes(30), setup);
    }

    @Test
    void setupBeforeTask_ignoresCancelledLaterDifferentTaskOnMachine() throws IOException {
        Instant t = Instant.parse("2026-05-22T08:00:00Z");
        repo.addAssignment(Assignment.planned(
                "done",
                "Z-1",
                "вал-буровой",
                1,
                "черновая-токарка",
                0,
                turning.machineId(),
                t,
                t.plus(Duration.ofMinutes(30))));
        repo.addAssignment(new Assignment(
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

        Duration setup = SetupPlanner.setupBeforeTask(turning, "вал-буровой", "черновая-токарка", repo);
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
