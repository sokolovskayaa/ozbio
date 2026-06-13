package scheduler.engine;

import scheduler.store.ScheduleStore;

public final class PartPriorities {
    private PartPriorities() {}

    public static int of(ScheduleStore store, String partId) {
        return store.partPriority(partId);
    }
}
