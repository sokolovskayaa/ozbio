package scheduler.store;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import scheduler.api.GsonConfig;
import scheduler.model.Capability;
import scheduler.model.Task;

public class JsonScheduleRepository {
    private final Path filePath;
    private final Gson gson = GsonConfig.createPretty();

    public JsonScheduleRepository(Path filePath) {
        this.filePath = filePath;
    }

    public static JsonScheduleRepository defaultRepository() {
        return new JsonScheduleRepository(Path.of("data", "schedule.json"));
    }

    public ScheduleStore loadOrCreate() throws IOException {
        if (!Files.exists(filePath)) {
            Files.createDirectories(filePath.getParent());
            Instant now = Instant.now();
            ScheduleStore store = ScheduleStore.empty(now, true, now);
            seedDefaultPartDefinitions(store);
            save(store);
            return store;
        }
        String json = Files.readString(filePath);
        ScheduleSnapshot snapshot = gson.fromJson(json, ScheduleSnapshot.class);
        if (snapshot == null || snapshot.factoryStartedAt == null) {
            Instant now = Instant.now();
            return ScheduleStore.empty(now, true, now);
        }
        return ScheduleStore.fromSnapshot(snapshot);
    }

    private static void seedDefaultPartDefinitions(ScheduleStore store) {
        store.setPartDefinition(
                "корпус-бура",
                new PartDefinition(
                        10,
                        List.of(
                                new Task("черновая-фрезеровка", 0, Duration.ofMinutes(90), Capability.MILLING),
                                new Task("расточивание-отверстий", 1, Duration.ofMinutes(120), Capability.DEEP_BORING),
                                new Task("чистовая-фрезеровка", 2, Duration.ofMinutes(60), Capability.MILLING))));
        store.setPartDefinition(
                "вал-буровой",
                new PartDefinition(
                        8,
                        List.of(
                                new Task("черновая-токарка", 0, Duration.ofMinutes(70), Capability.TURNING),
                                new Task("чистовая-токарка", 1, Duration.ofMinutes(45), Capability.TURNING),
                                new Task("шлифование-сегментов", 2, Duration.ofMinutes(50), Capability.GRINDING))));
        store.setPartDefinition(
                "гидроблок",
                new PartDefinition(
                        5,
                        List.of(
                                new Task("фрезерование-плоскостей", 0, Duration.ofMinutes(55), Capability.MILLING),
                                new Task("сверление-гидроканалов", 1, Duration.ofMinutes(40), Capability.DEEP_BORING))));
        store.setPartDefinition(
                "муфта-зажимная",
                new PartDefinition(
                        3,
                        List.of(
                                new Task("токарка-муфты", 0, Duration.ofMinutes(35), Capability.TURNING),
                                new Task("сварка-шва-MIG", 1, Duration.ofMinutes(25), Capability.WELDING))));
        store.setPartDefinition(
                "ниппель-соединительный",
                new PartDefinition(
                        4,
                        List.of(new Task("сборка-уплотнения", 0, Duration.ofMinutes(30), Capability.ASSEMBLY))));
    }

    public void save(ScheduleStore store) throws IOException {
        Files.createDirectories(filePath.getParent());
        ScheduleSnapshot snapshot = store.toSnapshot();
        Files.writeString(filePath, gson.toJson(snapshot));
    }
}
