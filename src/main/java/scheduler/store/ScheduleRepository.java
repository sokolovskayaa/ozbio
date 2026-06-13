package scheduler.store;

import java.io.IOException;
import java.time.Instant;
import scheduler.store.core.ScheduleStore;

/** Загрузка состояния и сохранение результатов планирования. */
public interface ScheduleRepository {

    /** Снимок завода из БД (каталог, заказы, план, станки). */
    ScheduleStore loadState() throws IOException;

    /** Время старта завода из {@code factory_state}. */
    Instant factoryStartedAt() throws IOException;

    /**
     * Сохраняет новый заказ, его назначения и обновлённые доступности станков.
     * Каталог (детали, группы) не перезаписывается.
     */
    void persistOrderScheduling(ScheduleStore store, String orderId) throws IOException;
}
