package ru.ozbio.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import ru.ozbio.api.dto.MachineShiftClosableItemResponse;
import ru.ozbio.api.dto.MachineShiftClosableResponse;
import ru.ozbio.api.dto.MachineShiftClosableShiftTypeResponse;
import ru.ozbio.persistence.MachineShiftInstanceRepository;
import ru.ozbio.service.model.MachineShiftClosableSummary;

@Service
public class MachineShiftQueryService {

    private final MachineShiftInstanceRepository machineShiftInstanceRepository;

    public MachineShiftQueryService(MachineShiftInstanceRepository machineShiftInstanceRepository) {
        this.machineShiftInstanceRepository = machineShiftInstanceRepository;
    }

    public MachineShiftClosableResponse findClosable() {
        List<MachineShiftClosableItemResponse> items =
                machineShiftInstanceRepository.findClosable(Instant.now()).stream()
                        .map(MachineShiftQueryService::toResponse)
                        .toList();
        return new MachineShiftClosableResponse(items);
    }

    private static MachineShiftClosableItemResponse toResponse(MachineShiftClosableSummary shift) {
        return new MachineShiftClosableItemResponse(
                shift.id(),
                shift.machineId(),
                shift.shiftTypeId(),
                shift.workDate(),
                shift.windowStart(),
                shift.windowEnd(),
                shift.status(),
                new MachineShiftClosableShiftTypeResponse(
                        shift.shiftTypeDayOfWeek(),
                        shift.shiftTypeStartTime(),
                        shift.shiftTypeEndTime()));
    }
}
