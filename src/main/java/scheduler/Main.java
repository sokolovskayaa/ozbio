package scheduler;

import java.io.IOException;
import scheduler.api.SchedulerHttpServer;
import scheduler.store.JsonScheduleRepository;
import scheduler.store.ScheduleStore;
import scheduler.service.SchedulerService;
import scheduler.time.StoreCurrentTimeProvider;

public class Main {
    public static void main(String[] args) throws IOException {
        JsonScheduleRepository repository = JsonScheduleRepository.defaultRepository();
        ScheduleStore store = repository.loadOrCreate();
        StoreCurrentTimeProvider time = new StoreCurrentTimeProvider(store);
        SchedulerService service = new SchedulerService(store, repository, time);
        SchedulerHttpServer http = new SchedulerHttpServer(service, 8080);
        http.start();

        System.out.println("Factory scheduler — data/schedule.json");
        System.out.println("Overlap batches: " + (store.overlapBatchesEnabled() ? "ON" : "OFF")
                + " (scheduling.overlapBatches / -Dscheduler.overlapBatches)");
        System.out.println("Factory started: " + store.factoryStartedAt());
        System.out.println("Current time:    " + time.now()
                + (time.isSimulation() ? " (simulation)" : " (system)"));
        System.out.println("POST http://localhost:8080/orders       — add order");
        System.out.println("GET  http://localhost:8080/schedule       — JSON plan");
        System.out.println("GET  http://localhost:8080/schedule.html   — HTML (download)");
        System.out.println("GET  http://localhost:8080/schedule?format=html — HTML in browser");
        System.out.println("PUT  http://localhost:8080/time          — set simulation time (JSON)");
        System.out.println("GET  http://localhost:8080/shifts/context — shift close form (aggregates)");
        System.out.println("POST http://localhost:8080/shifts/close    — close shift (counts or per-op facts)");
        System.out.println("PATCH http://localhost:8080/machines/ТОКАР-ЧПУ-02 — set machine status (URL-encode id)");
        System.out.println();
        System.out.println("Demo: ./scripts/reset-demo-data.sh  then  ./scripts/demo.sh");
    }
}
