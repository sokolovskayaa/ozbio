package scheduler.time;

import java.time.Instant;

public final class SystemCurrentTimeProvider implements CurrentTimeProvider {
    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public boolean isSimulation() {
        return false;
    }
}
