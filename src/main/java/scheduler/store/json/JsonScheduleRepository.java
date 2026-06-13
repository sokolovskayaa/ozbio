package scheduler.store.json;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import scheduler.api.config.GsonConfig;
import scheduler.store.ScheduleRepository;
import scheduler.store.core.CatalogSeeder;
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

    @Override
    public ScheduleStore loadOrCreate() throws IOException {
        if (!Files.exists(filePath)) {
            Files.createDirectories(filePath.getParent());
            Instant now = Instant.now();
            ScheduleStore store = ScheduleStore.empty(now);
            CatalogSeeder.seedPartDefinitions(store);
            save(store);
            return store;
        }
        String json = Files.readString(filePath);
        ScheduleSnapshot snapshot = gson.fromJson(json, ScheduleSnapshot.class);
        if (snapshot == null || snapshot.factoryStartedAt == null) {
            return ScheduleStore.empty(Instant.now());
        }
        return ScheduleStore.fromSnapshot(snapshot);
    }

    @Override
    public void save(ScheduleStore store) throws IOException {
        Files.createDirectories(filePath.getParent());
        ScheduleSnapshot snapshot = store.toSnapshot();
        Files.writeString(filePath, gson.toJson(snapshot));
    }
}
