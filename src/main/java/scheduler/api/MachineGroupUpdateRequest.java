package scheduler.api;

import java.time.Duration;
import java.util.List;

public record MachineGroupUpdateRequest(List<WorkWindowRequest> workWindows, Long setupMinutes) {

    public record WorkWindowRequest(String dayOfWeek, String start, String end) {}

    public Duration setupDuration() {
        if (setupMinutes == null || setupMinutes < 0) {
            return Duration.ZERO;
        }
        return Duration.ofMinutes(setupMinutes);
    }
}
