package scheduler.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import scheduler.engine.FactoryZone;
import scheduler.engine.ShiftCalendar;
import scheduler.engine.ShiftCalendar.ShiftWindow;
import scheduler.model.MachineGroup;
import scheduler.store.ScheduleStore;

/** Прошедшие смены, ещё не отмеченные закрытыми. */
final class ShiftPendingShifts {
    record Entry(String groupId, String groupName, ShiftWindow window) {}

    private ShiftPendingShifts() {}

    static List<Entry> list(ScheduleStore store, Instant now) {
        List<Entry> all = new ArrayList<>();
        for (MachineGroup group : store.machineGroups().values()) {
            Instant after = store.lastClosedShiftEnd(group.groupId());
            if (after == null) {
                after = store.factoryStartedAt();
            }
            for (ShiftWindow window :
                    ShiftCalendar.shiftWindowsEndingAfter(after, now, group, FactoryZone.ZONE)) {
                all.add(new Entry(group.groupId(), group.name(), window));
            }
        }
        return List.copyOf(all);
    }

    static boolean hasPlannedWork(ScheduleStore store, Entry entry) {
        return !ShiftAssignments.aggregateByMachineAndTask(
                        store, entry.groupId(), entry.window().start(), entry.window().end())
                .isEmpty();
    }
}
