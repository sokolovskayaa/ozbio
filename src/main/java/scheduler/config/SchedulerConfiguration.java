package scheduler.config;

import java.io.IOException;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import scheduler.store.ScheduleRepository;
import scheduler.time.CurrentTimeProvider;
import scheduler.time.SystemCurrentTimeProvider;

@Configuration
public class SchedulerConfiguration {

    @Bean
    CurrentTimeProvider currentTimeProvider() {
        return new SystemCurrentTimeProvider();
    }

    @Bean
    ApplicationRunner startupLog(ScheduleRepository repository, Environment env) throws IOException {
        String port = env.getProperty("local.server.port", env.getProperty("server.port", "8080"));
        String datasource = env.getProperty("spring.datasource.url", "—");
        String schema = env.getProperty("app.db.schema", "—");
        return args -> {
            System.out.println("Factory scheduler MVP — PostgreSQL");
            System.out.println("Schema: " + schema);
            System.out.println("Datasource: " + datasource);
            System.out.println("Factory started: " + repository.factoryStartedAt());
            System.out.println("POST http://localhost:" + port + "/orders       — add order");
            System.out.println("GET  http://localhost:" + port + "/schedule       — JSON plan");
            System.out.println("GET  http://localhost:" + port + "/schedule?format=html — HTML in browser");
        };
    }
}
