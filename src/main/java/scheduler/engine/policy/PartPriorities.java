package scheduler.engine.policy;

import scheduler.store.core.ScheduleStore;

public final class PartPriorities {
    private PartPriorities() {}

    public static int of(ScheduleStore store, String partId) {
        return store.partPriority(partId);
    }
}
