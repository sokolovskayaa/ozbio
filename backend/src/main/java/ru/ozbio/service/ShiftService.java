package ru.ozbio.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ozbio.api.dto.CreateShiftTypeRequest;
import ru.ozbio.api.dto.ShiftTypeResponse;
import ru.ozbio.persistence.ShiftRepository;
import ru.ozbio.service.model.ShiftTypeSummary;

@Service
public class ShiftService {

    private final ShiftRepository shiftRepository;

    public ShiftService(ShiftRepository shiftRepository) {
        this.shiftRepository = shiftRepository;
    }

    @Transactional
    public ShiftTypeResponse create(CreateShiftTypeRequest request) {
        return toResponse(shiftRepository.insert(request.toCommand()));
    }

    @Transactional
    public void delete(long id) {
        shiftRepository.deleteById(id);
    }

    private static ShiftTypeResponse toResponse(ShiftTypeSummary shiftType) {
        return new ShiftTypeResponse(
                shiftType.id(), shiftType.dayOfWeek(), shiftType.startTime(), shiftType.endTime());
    }
}
