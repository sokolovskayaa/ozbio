package scheduler.engine;

import java.time.Instant;
import scheduler.model.Assignment;
import scheduler.model.AssignmentStatus;
import scheduler.model.MachineBlock;
import scheduler.store.ScheduleStore;

public final class MachineTimeline {
    private MachineTimeline() {}

    /** Момент, с которого станок свободен для нового планирования (факты + простои + now). */
    public static Instant availableFrom(ScheduleStore store, String machineId, Instant now) {
        Instant baseline = store.factoryStartedAt();
        Instant latest = baseline.isBefore(now) ? now : baseline;

        for (Assignment a : store.assignments()) {
            if (!a.machineId().equals(machineId) || a.status() == AssignmentStatus.CANCELLED) {
                continue;
            }
            Instant end = a.effectiveEnd();
            if (end.isAfter(latest)) {
                latest = end;
            }
        }
        for (MachineBlock block : store.machineBlocks()) {
            if (!block.machineId().equals(machineId)) {
                continue;
            }
            if (block.to().isAfter(latest)) {
                latest = block.to();
            }
        }
        return latest;
    }

    /** Сдвигает якорь за пределы всех блокировок, в которые он попадает. */
    public static Instant afterBlocks(ScheduleStore store, String machineId, Instant anchor) {
        Instant cursor = anchor;
        boolean changed;
        do {
            changed = false;
            for (MachineBlock block : store.machineBlocks()) {
                if (!block.machineId().equals(machineId)) {
                    continue;
                }
                if (!cursor.isBefore(block.from()) && cursor.isBefore(block.to())) {
                    cursor = block.to();
                    changed = true;
                }
            }
        } while (changed);
        return cursor;
    }
}
