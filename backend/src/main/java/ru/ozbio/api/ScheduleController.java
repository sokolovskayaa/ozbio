package ru.ozbio.api;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ozbio.api.dto.ScheduleItemResponse;
import ru.ozbio.service.ScheduleService;

@RestController
@RequestMapping(path = "/schedule", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Schedule", description = "Расписание производства")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    @Operation(summary = "Текущее расписание")
    List<ScheduleItemResponse> current() {
        return scheduleService.current();
    }
}
