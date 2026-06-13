package scheduler.engine;

import java.time.Instant;
import scheduler.model.Machine;
import scheduler.model.MachineStatus;
import scheduler.store.ScheduleStore;

public final class MachineStateSync {
    private MachineStateSync() {}

    /**
     * Обновляет {@code availableAt} и IDLE/BUSY по расписанию и {@code now}.
     * DOWN / MAINTENANCE / SETUP не перезаписываются.
     */
    public static void sync(ScheduleStore store, Instant now) {
        for (Machine machine : store.machines()) {
            if (!machine.isOperational()) {
                continue;
            }
            Instant effective = MachineTimeline.availableFrom(store, machine.machineId(), now);
            machine.setAvailableAt(effective);
            machine.setStatus(machine.availableAt().isAfter(now) ? MachineStatus.BUSY : MachineStatus.IDLE);
        }
    }
}
