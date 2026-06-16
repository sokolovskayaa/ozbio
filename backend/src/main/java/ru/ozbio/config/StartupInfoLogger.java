package ru.ozbio.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class StartupInfoLogger implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(StartupInfoLogger.class);

    @Value("${server.port:8080}")
    private int port;

    @Value("${springdoc.swagger-ui.path:/swagger-ui.html}")
    private String swaggerUiPath;

    @Value("${springdoc.api-docs.path:/api-docs}")
    private String apiDocsPath;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        String baseUrl = "http://localhost:" + port;
        log.info("Swagger UI: {}", baseUrl + normalizePath(swaggerUiPath));
        log.info("OpenAPI:    {}", baseUrl + normalizePath(apiDocsPath));
    }

    private static String normalizePath(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }
}
