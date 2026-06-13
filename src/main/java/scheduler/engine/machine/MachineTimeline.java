package scheduler.engine.machine;

import java.time.Instant;
import scheduler.store.core.ScheduleStore;

public final class MachineTimeline {
    private MachineTimeline() {}

    public static Instant availableFrom(ScheduleStore store, String machineId, Instant now) {
        Instant baseline = store.factoryStartedAt();
        Instant latest = baseline.isBefore(now) ? now : baseline;
        Instant machineAvailable = store.findMachine(machineId).availableAt();
        if (machineAvailable.isAfter(latest)) {
            latest = machineAvailable;
        }

        for (var a : store.assignments()) {
            if (!a.machineId().equals(machineId) || a.status() == scheduler.model.schedule.AssignmentStatus.CANCELLED) {
                continue;
            }
            Instant end = a.effectiveEnd();
            if (end.isAfter(latest)) {
                latest = end;
            }
        }
        return latest;
    }
}
