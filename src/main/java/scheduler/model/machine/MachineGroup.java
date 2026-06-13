package scheduler.model.machine;

import java.time.Duration;

/** Группа станков с общим временем переналадки. */
public record MachineGroup(String groupId, String name, Duration setupDuration) {

    public MachineGroup {
        if (setupDuration == null || setupDuration.isNegative()) {
            setupDuration = Duration.ZERO;
        }
    }
}
