package ru.ozbio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.shift-generation")
public record ShiftGenerationProperties(String factoryZoneId, int horizonDays, String cron) {

    public ShiftGenerationProperties {
        if (factoryZoneId == null || factoryZoneId.isBlank()) {
            factoryZoneId = "Europe/Moscow";
        }
        if (horizonDays <= 0) {
            horizonDays = 3;
        }
        if (cron == null || cron.isBlank()) {
            cron = "0 5 0 * * *";
        }
    }
}
