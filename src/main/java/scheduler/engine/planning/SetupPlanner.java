package scheduler.engine.planning;

import java.time.Duration;
import java.util.Optional;
import scheduler.model.schedule.Assignment;
import scheduler.model.machine.Machine;
import scheduler.model.machine.MachineGroup;
import scheduler.store.PlanningRepository;

public final class SetupPlanner {
    private SetupPlanner() {}

    /**
     * Переналадка перед рабочей операцией: если на станке последняя операция была с **другим**
     * {@code taskId} (тип операции), либо станок пуст — длительность из группы; иначе 0.
     */
    public static Duration setupBeforeTask(
            Machine machine, String partId, String taskId, PlanningRepository repo) throws java.io.IOException {
        Optional<MachineGroup> group = repo.groupForMachine(machine.machineId());
        Duration setup = group.isPresent() ? group.get().setupDuration() : Duration.ZERO;
        if (setup.isZero()) {
            return Duration.ZERO;
        }
        Optional<Assignment> last = repo.lastWorkOnMachine(machine.machineId());
        if (last.isEmpty()) {
            return setup;
        }
        if (last.get().partId().equals(partId) && last.get().taskId().equals(taskId)) {
            return Duration.ZERO;
        }
        return setup;
    }
}
