package scheduler.store.snapshot;

import java.time.Duration;
import scheduler.model.machine.MachineGroup;
import scheduler.model.machine.MachineGroupDefaults;

public class MachineGroupSnapshot {
    public String groupId;
    public String name;
    /** Удобная настройка перед запуском (минуты). */
    public Integer setupMinutes;
    /** Альтернатива: ISO-8601, например PT30M. */
    public Duration setupDuration;

    public MachineGroup toGroup() {
        return new MachineGroup(groupId, name, resolveSetupDuration(groupId, setupMinutes, setupDuration));
    }

    public static MachineGroupSnapshot from(MachineGroup group) {
        MachineGroupSnapshot dto = new MachineGroupSnapshot();
        dto.groupId = group.groupId();
        dto.name = group.name();
        long minutes = group.setupDuration().toMinutes();
        dto.setupMinutes = minutes > 0 ? (int) minutes : null;
        dto.setupDuration = group.setupDuration();
        return dto;
    }

    private static Duration resolveSetupDuration(
            String groupId, Integer setupMinutes, Duration setupDuration) {
        if (setupMinutes != null && setupMinutes > 0) {
            return Duration.ofMinutes(setupMinutes);
        }
        if (setupDuration != null && !setupDuration.isNegative() && !setupDuration.isZero()) {
            return setupDuration;
        }
        return MachineGroupDefaults.setupDuration(groupId);
    }
}
