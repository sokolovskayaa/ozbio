package scheduler.store;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import scheduler.model.MachineGroup;
import scheduler.model.WorkWindow;

public class MachineGroupSnapshot {
    public String groupId;
    public String name;
    public List<WorkWindowSnapshot> workWindows = new ArrayList<>();
    /** Удобная настройка перед запуском (минуты). */
    public Integer setupMinutes;
    /** Альтернатива: ISO-8601, например PT30M. */
    public Duration setupDuration;

    public MachineGroup toGroup() {
        List<WorkWindow> windows = workWindows == null
                ? List.of()
                : workWindows.stream().map(WorkWindowSnapshot::toWindow).toList();
        Duration setup = resolveSetupDuration(groupId, setupMinutes, setupDuration);
        return new MachineGroup(groupId, name, windows, setup);
    }

    public static MachineGroupSnapshot from(MachineGroup group) {
        MachineGroupSnapshot dto = new MachineGroupSnapshot();
        dto.groupId = group.groupId();
        dto.name = group.name();
        long minutes = group.setupDuration().toMinutes();
        dto.setupMinutes = minutes > 0 ? (int) minutes : null;
        dto.setupDuration = group.setupDuration();
        dto.workWindows = group.workWindows().stream()
                .map(WorkWindowSnapshot::from)
                .toList();
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
        return scheduler.model.MachineGroupDefaults.setupDuration(groupId);
    }
}
