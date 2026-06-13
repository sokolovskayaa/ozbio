package scheduler.engine.policy;

import scheduler.store.PlanningRepository;

public final class PartPriorities {
    private PartPriorities() {}

    public static int of(PlanningRepository repo, String partId) throws java.io.IOException {
        return repo.partPriority(partId);
    }
}
