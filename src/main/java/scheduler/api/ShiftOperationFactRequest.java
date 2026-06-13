package scheduler.api;

import java.time.Instant;

public record ShiftOperationFactRequest(
        String orderId,
        String partId,
        int unitIndex,
        String taskId,
        boolean completed,
        Instant actualStart,
        Instant actualEnd) {}
