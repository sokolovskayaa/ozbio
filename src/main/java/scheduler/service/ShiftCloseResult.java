package scheduler.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ShiftCloseResult(
        int completedCount,
        int cancelledCount,
        int replanCancelledAssignments,
        List<String> replannedOrderIds,
        Map<String, Instant> orderReadyAt) {}
