package scheduler.store;

import java.io.IOException;
import java.time.Instant;

/** Точечные чтения из БД для GET /schedule. */
public interface ScheduleQueryRepository {

    Instant factoryStartedAt() throws IOException;

    ScheduleData loadScheduleData() throws IOException;
}
