package scheduler;

import java.io.IOException;
import scheduler.api.SchedulerHttpServer;
import scheduler.store.JsonScheduleRepository;
import scheduler.store.ScheduleStore;
import scheduler.service.SchedulerService;
import scheduler.time.SystemCurrentTimeProvider;

public class Main {
    public static void main(String[] args) throws IOException {
        JsonScheduleRepository repository = JsonScheduleRepository.defaultRepository();
        ScheduleStore store = repository.loadOrCreate();
        SystemCurrentTimeProvider time = new SystemCurrentTimeProvider();
        SchedulerService service = new SchedulerService(store, repository, time);
        SchedulerHttpServer http = new SchedulerHttpServer(service, 8080);
        http.start();

        System.out.println("Factory scheduler MVP — data/schedule.json");
        System.out.println("Factory started: " + store.factoryStartedAt());
        System.out.println("POST http://localhost:8080/orders       — add order");
        System.out.println("GET  http://localhost:8080/schedule       — JSON plan");
        System.out.println("GET  http://localhost:8080/schedule?format=html — HTML in browser");
    }
}
