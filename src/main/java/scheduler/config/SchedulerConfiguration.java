package scheduler.config;

import java.io.IOException;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import scheduler.store.ScheduleRepository;
import scheduler.store.core.ScheduleStore;
import scheduler.time.CurrentTimeProvider;
import scheduler.time.SystemCurrentTimeProvider;

@Configuration
public class SchedulerConfiguration {

    @Bean
    ScheduleStore scheduleStore(ScheduleRepository repository) throws IOException {
        return repository.loadOrCreate();
    }

    @Bean
    CurrentTimeProvider currentTimeProvider() {
        return new SystemCurrentTimeProvider();
    }

    @Bean
    ApplicationRunner startupLog(ScheduleStore store, Environment env) {
        String port = env.getProperty("local.server.port", env.getProperty("server.port", "8080"));
        String datasource = env.getProperty("spring.datasource.url", "—");
        return args -> {
            System.out.println("Factory scheduler MVP — PostgreSQL");
            System.out.println("Datasource: " + datasource);
            System.out.println("Factory started: " + store.factoryStartedAt());
            System.out.println("POST http://localhost:" + port + "/orders       — add order");
            System.out.println("GET  http://localhost:" + port + "/schedule       — JSON plan");
            System.out.println("GET  http://localhost:" + port + "/schedule?format=html — HTML in browser");
        };
    }
}
