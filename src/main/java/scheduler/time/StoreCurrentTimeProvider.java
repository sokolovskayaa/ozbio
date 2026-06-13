package scheduler.time;

import java.time.Instant;
import scheduler.store.ScheduleStore;

/**
 * Если в store включена симуляция — возвращает заданное время, иначе системные часы.
 */
public final class StoreCurrentTimeProvider implements CurrentTimeProvider {
    private final ScheduleStore store;

    public StoreCurrentTimeProvider(ScheduleStore store) {
        this.store = store;
    }

    @Override
    public Instant now() {
        if (store.simulationClockEnabled()) {
            return store.simulationCurrentTime();
        }
        return Instant.now();
    }

    @Override
    public boolean isSimulation() {
        return store.simulationClockEnabled();
    }
}
