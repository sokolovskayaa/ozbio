package scheduler.time;

import java.time.Instant;

/** Абстракция «сейчас» — чтобы убрать симуляцию времени, подставьте {@link SystemCurrentTimeProvider}. */
public interface CurrentTimeProvider {
    Instant now();

    boolean isSimulation();
}
