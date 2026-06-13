package scheduler.store.json;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import scheduler.api.config.GsonConfig;
import scheduler.store.ScheduleRepository;
import scheduler.store.core.CatalogSeeder;
import scheduler.store.core.ScheduleSnapshotMapper;
import scheduler.store.core.ScheduleStore;

public class JsonScheduleRepository implements ScheduleRepository {
    private final Path filePath;
    private final Gson gson = GsonConfig.createPretty();

    public JsonScheduleRepository(Path filePath) {
        this.filePath = filePath;
    }

    public static JsonScheduleRepository defaultRepository() {
        return new JsonScheduleRepository(Path.of("data", "schedule.json"));
    }

    /** Для тестов: первичная запись состояния в файл. */
    public void writeState(ScheduleStore store) throws IOException {
        writeFile(store);
    }

    @Override
    public ScheduleStore loadState() throws IOException {
        if (!Files.exists(filePath)) {
            Files.createDirectories(filePath.getParent());
            Instant now = Instant.now();
            ScheduleStore store = ScheduleStore.empty(now);
            CatalogSeeder.seedPartDefinitions(store);
            writeFile(store);
            return store;
        }
        String json = Files.readString(filePath);
        ScheduleSnapshot snapshot = gson.fromJson(json, ScheduleSnapshot.class);
        if (snapshot == null || snapshot.factoryStartedAt == null) {
            throw new IOException("Invalid schedule file: " + filePath);
        }
        return ScheduleStore.fromSnapshot(snapshot);
    }

    @Override
    public Instant factoryStartedAt() throws IOException {
        return loadState().factoryStartedAt();
    }

    @Override
    public void persistOrderScheduling(ScheduleStore store, String orderId) throws IOException {
        writeFile(store);
    }

    private void writeFile(ScheduleStore store) throws IOException {
        Files.createDirectories(filePath.getParent());
        ScheduleSnapshot snapshot = store.toSnapshot();
        Files.writeString(filePath, gson.toJson(snapshot));
    }
}
