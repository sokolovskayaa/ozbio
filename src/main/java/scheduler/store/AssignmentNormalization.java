package scheduler.store;

import java.time.Instant;
import scheduler.model.Assignment;
import scheduler.model.AssignmentStatus;

final class AssignmentNormalization {
    private AssignmentNormalization() {}

    static Assignment normalize(Assignment a) {
        AssignmentStatus status = a.status() != null ? a.status() : AssignmentStatus.PLANNED;
        Instant actualStart = a.actualStart();
        Instant actualEnd = a.actualEnd();
        if (status == AssignmentStatus.COMPLETED) {
            if (actualStart == null) {
                actualStart = a.plannedStart();
            }
            if (actualEnd == null) {
                actualEnd = a.plannedEnd();
            }
        }
        return new Assignment(
                a.assignmentId(),
                a.orderId(),
                a.partId(),
                a.unitIndex(),
                a.taskId(),
                a.sequence(),
                a.machineId(),
                a.plannedStart(),
                a.plannedEnd(),
                status,
                actualStart,
                actualEnd);
    }
}
