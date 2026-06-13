package scheduler.engine;

import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import scheduler.model.Assignment;
import scheduler.model.Machine;
import scheduler.model.MachineGroup;
import scheduler.model.SetupIntervals;
import scheduler.store.ScheduleStore;

public final class SetupPlanner {
    private SetupPlanner() {}

    /**
     * Переналадка перед рабочей операцией: если на станке последняя операция была с **другим**
     * {@code taskId} (тип операции), либо станок пуст — длительность из группы; иначе 0.
     */
    public static Duration setupBeforeTask(
            Machine machine, String partId, String taskId, ScheduleStore store) {
        MachineGroup group = store.findGroupForMachine(machine).orElse(null);
        Duration setup = group != null ? group.setupDuration() : Duration.ZERO;
        if (setup.isZero()) {
            return Duration.ZERO;
        }
        Optional<Assignment> last = AssignmentFilters.work(store.assignments()).stream()
                .filter(a -> a.machineId().equals(machine.machineId()))
                .filter(a -> a.isCompleted() || a.isPlanned())
                .max(Comparator.comparing(Assignment::effectiveEnd));
        if (last.isEmpty()) {
            return setup;
        }
        if (last.get().partId().equals(partId) && last.get().taskId().equals(taskId)) {
            return Duration.ZERO;
        }
        return setup;
    }
}
