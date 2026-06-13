package scheduler.model;

import java.time.Duration;
import java.util.List;

/** Группа станков с общим расписанием смен и временем переналадки. */
public record MachineGroup(
        String groupId,
        String name,
        List<WorkWindow> workWindows,
        Duration setupDuration) {

    public MachineGroup {
        workWindows = List.copyOf(workWindows);
        if (setupDuration == null || setupDuration.isNegative()) {
            setupDuration = Duration.ZERO;
        }
    }

    /** Круглосуточно, без переналадки по умолчанию. */
    public static MachineGroup alwaysOn(String groupId, String name) {
        return new MachineGroup(groupId, name, List.of(), Duration.ZERO);
    }
}
