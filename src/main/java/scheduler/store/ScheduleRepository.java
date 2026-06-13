package scheduler.store;

import java.io.IOException;
import scheduler.store.core.ScheduleStore;

/** Загрузка и сохранение состояния планировщика. */
public interface ScheduleRepository {

    ScheduleStore loadOrCreate() throws IOException;

    void save(ScheduleStore store) throws IOException;
}
