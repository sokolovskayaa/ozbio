package scheduler.engine;

import java.util.List;
import scheduler.model.Assignment;
import scheduler.model.AssignmentStatus;
import scheduler.model.SetupIntervals;

public final class AssignmentFilters {
    private AssignmentFilters() {}

    public static List<Assignment> active(List<Assignment> assignments) {
        return assignments.stream().filter(a -> a.status() != AssignmentStatus.CANCELLED).toList();
    }

    public static List<Assignment> work(List<Assignment> assignments) {
        return active(assignments).stream().filter(a -> !SetupIntervals.isSetup(a.taskId())).toList();
    }

    public static List<Assignment> plannedWork(List<Assignment> assignments) {
        return work(assignments).stream().filter(Assignment::isPlanned).toList();
    }

    public static List<Assignment> completedWork(List<Assignment> assignments) {
        return work(assignments).stream().filter(Assignment::isCompleted).toList();
    }
}
