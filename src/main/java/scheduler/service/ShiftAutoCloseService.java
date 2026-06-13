package scheduler.service;

import java.io.IOException;
import java.time.Instant;
import scheduler.store.JsonScheduleRepository;
import scheduler.store.ScheduleStore;
import scheduler.time.CurrentTimeProvider;

/** Закрытие смен без запланированных операций (только отметка в {@code lastClosedShiftEndByGroup}). */
public class ShiftAutoCloseService {
    private final ScheduleStore store;
    private final JsonScheduleRepository repository;
    private final CurrentTimeProvider time;

    public ShiftAutoCloseService(
            ScheduleStore store, JsonScheduleRepository repository, CurrentTimeProvider time) {
        this.store = store;
        this.repository = repository;
        this.time = time;
    }

    /**
     * Помечает закрытыми все просроченные смены без {@code PLANNED} работ в окне смены.
     *
     * @return сколько смен закрыто
     */
    public int closeEmptyPendingShifts() throws IOException {
        Instant now = time.now();
        int closed = 0;
        while (true) {
            boolean any = false;
            for (ShiftPendingShifts.Entry entry : ShiftPendingShifts.list(store, now)) {
                if (ShiftPendingShifts.hasPlannedWork(store, entry)) {
                    continue;
                }
                store.setLastClosedShiftEnd(entry.groupId(), entry.window().end());
                closed++;
                any = true;
            }
            if (!any) {
                break;
            }
        }
        if (closed > 0) {
            repository.save(store);
        }
        return closed;
    }
}
