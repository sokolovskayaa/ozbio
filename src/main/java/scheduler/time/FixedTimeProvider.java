package scheduler.time;

import java.time.Instant;

/** Фиксированное «сейчас» для тестов и демо. */
public record FixedTimeProvider(Instant fixed) implements CurrentTimeProvider {
    @Override
    public Instant now() {
        return fixed;
    }

    @Override
    public boolean isSimulation() {
        return false;
    }
}
