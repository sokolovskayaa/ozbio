package scheduler.engine.machine;

import java.time.Instant;
import scheduler.store.PlanningRepository;

public final class MachineTimeline {
    private MachineTimeline() {}

    public static Instant availableFrom(PlanningRepository repo, String machineId, Instant now)
            throws java.io.IOException {
        return repo.machineAvailableFrom(machineId, now);
    }
}
