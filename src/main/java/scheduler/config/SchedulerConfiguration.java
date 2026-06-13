package scheduler.config;

import java.io.IOException;
import java.nio.file.Path;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import scheduler.store.core.ScheduleStore;
import scheduler.store.json.JsonScheduleRepository;
import scheduler.time.CurrentTimeProvider;
import scheduler.time.SystemCurrentTimeProvider;

@Configuration
public class SchedulerConfiguration {

    @Bean
    JsonScheduleRepository jsonScheduleRepository(SchedulerProperties properties) {
        return new JsonScheduleRepository(Path.of(properties.dataFile()));
    }

    @Bean
    ScheduleStore scheduleStore(JsonScheduleRepository repository) throws IOException {
        return repository.loadOrCreate();
    }

    @Bean
    CurrentTimeProvider currentTimeProvider() {
        return new SystemCurrentTimeProvider();
    }

    @Bean
    ApplicationRunner startupLog(ScheduleStore store, SchedulerProperties properties, Environment env) {
        String port = env.getProperty("local.server.port", env.getProperty("server.port", "8080"));
        return args -> {
            System.out.println("Factory scheduler MVP — " + properties.dataFile());
            System.out.println("Factory started: " + store.factoryStartedAt());
            System.out.println("POST http://localhost:" + port + "/orders       — add order");
            System.out.println("GET  http://localhost:" + port + "/schedule       — JSON plan");
            System.out.println("GET  http://localhost:" + port + "/schedule?format=html — HTML in browser");
        };
    }
}
