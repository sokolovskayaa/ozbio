package ru.ozbio.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.ozbio.service.ShiftGenerationService;
import ru.ozbio.service.model.ShiftGenerationResult;

@Component
public class ShiftGenerationJob {

    private static final Logger log = LoggerFactory.getLogger(ShiftGenerationJob.class);

    private final ShiftGenerationService shiftGenerationService;

    public ShiftGenerationJob(ShiftGenerationService shiftGenerationService) {
        this.shiftGenerationService = shiftGenerationService;
    }

    @Scheduled(cron = "${app.shift-generation.cron}")
    public void run() {
        ShiftGenerationResult result = shiftGenerationService.generate();
        log.info("Shift generation finished: upserted={}, deleted={}", result.upserted(), result.deleted());
    }
}
