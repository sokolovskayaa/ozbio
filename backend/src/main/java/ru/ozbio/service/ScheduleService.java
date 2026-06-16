package ru.ozbio.service;

import java.util.List;

import org.springframework.stereotype.Service;
import ru.ozbio.api.dto.ScheduleItemResponse;
import ru.ozbio.engine.ReplanService;
import ru.ozbio.engine.model.PlannedScheduleItem;

@Service
public class ScheduleService {

    private final ReplanService replanService;

    public ScheduleService(ReplanService replanService) {
        this.replanService = replanService;
    }

    public List<ScheduleItemResponse> current() {
        return replanService.replan().stream().map(ScheduleService::toResponse).toList();
    }

    private static ScheduleItemResponse toResponse(PlannedScheduleItem item) {
        return new ScheduleItemResponse(
                item.orderId(),
                item.operationId(),
                item.machineId(),
                item.count(),
                item.start(),
                item.end());
    }
}
