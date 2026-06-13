package scheduler.model;

import java.time.Instant;

public record Assignment(
        String assignmentId,
        String orderId,
        String partId,
        int unitIndex,
        String taskId,
        int sequence,
        String machineId,
        Instant plannedStart,
        Instant plannedEnd,
        AssignmentStatus status,
        Instant actualStart,
        Instant actualEnd) {

    public Assignment {
        if (status == null) {
            status = AssignmentStatus.PLANNED;
        }
    }

    /** Для новых плановых назначений без факта. */
    public static Assignment planned(
            String assignmentId,
            String orderId,
            String partId,
            int unitIndex,
            String taskId,
            int sequence,
            String machineId,
            Instant plannedStart,
            Instant plannedEnd) {
        return new Assignment(
                assignmentId,
                orderId,
                partId,
                unitIndex,
                taskId,
                sequence,
                machineId,
                plannedStart,
                plannedEnd,
                AssignmentStatus.PLANNED,
                null,
                null);
    }

    public Instant effectiveStart() {
        return actualStart != null ? actualStart : plannedStart;
    }

    public Instant effectiveEnd() {
        return actualEnd != null ? actualEnd : plannedEnd;
    }

    public boolean isCompleted() {
        return status == AssignmentStatus.COMPLETED;
    }

    public boolean isPlanned() {
        return status == AssignmentStatus.PLANNED;
    }

    public boolean isCancelled() {
        return status == AssignmentStatus.CANCELLED;
    }
}
