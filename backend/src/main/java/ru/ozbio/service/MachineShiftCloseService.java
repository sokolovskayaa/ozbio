package ru.ozbio.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ozbio.api.dto.CloseMachineShiftRequest;
import ru.ozbio.api.dto.ShiftCompletionRequest;
import ru.ozbio.persistence.DetailRepository;
import ru.ozbio.persistence.MachineRepository;
import ru.ozbio.persistence.MachineShiftInstanceRepository;
import ru.ozbio.persistence.ShiftCompletionRepository;
import ru.ozbio.service.exception.InvalidReferenceException;
import ru.ozbio.service.exception.OperationMachineTypeMismatchException;
import ru.ozbio.service.exception.ResourceNotFoundException;
import ru.ozbio.service.exception.ShiftAlreadyClosedException;
import ru.ozbio.service.model.MachineShiftCloseTarget;
import ru.ozbio.service.model.OperationLine;

@Service
public class MachineShiftCloseService {

    private static final Set<String> CLOSED_STATUSES = Set.of("CLOSED", "CLOSED_EMPTY");

    private final MachineShiftInstanceRepository machineShiftInstanceRepository;
    private final MachineRepository machineRepository;
    private final DetailRepository detailRepository;
    private final ShiftCompletionRepository shiftCompletionRepository;

    public MachineShiftCloseService(
            MachineShiftInstanceRepository machineShiftInstanceRepository,
            MachineRepository machineRepository,
            DetailRepository detailRepository,
            ShiftCompletionRepository shiftCompletionRepository) {
        this.machineShiftInstanceRepository = machineShiftInstanceRepository;
        this.machineRepository = machineRepository;
        this.detailRepository = detailRepository;
        this.shiftCompletionRepository = shiftCompletionRepository;
    }

    @Transactional
    public void close(long machineShiftId, CloseMachineShiftRequest request) {
        MachineShiftCloseTarget shift =
                machineShiftInstanceRepository
                        .findByIdForUpdate(machineShiftId)
                        .orElseThrow(() -> new ResourceNotFoundException("machineShiftId", machineShiftId));

        if (CLOSED_STATUSES.contains(shift.status())) {
            throw new ShiftAlreadyClosedException(machineShiftId);
        }

        Instant now = Instant.now();
        if (shift.windowStart().isAfter(now)) {
            throw new IllegalArgumentException("Shift has not started yet");
        }

        List<ShiftCompletionRequest> completions = request.completions();
        validateNoDuplicateOperationIds(completions);

        if (completions.isEmpty()) {
            machineShiftInstanceRepository.updateClosed(machineShiftId, "CLOSED_EMPTY", now);
            return;
        }

        long machineTypeId =
                machineRepository
                        .findMachineById(shift.machineId())
                        .orElseThrow(() -> new InvalidReferenceException("machineId", shift.machineId()))
                        .machineTypeId();

        Map<Long, OperationLine> operationsById =
                detailRepository.findOperationsByIds(
                        completions.stream().map(ShiftCompletionRequest::operationId).toList());

        for (ShiftCompletionRequest completion : completions) {
            OperationLine operation = operationsById.get(completion.operationId());
            if (operation == null) {
                throw new InvalidReferenceException("operationId", completion.operationId());
            }
            if (operation.machineTypeId() != machineTypeId) {
                throw new OperationMachineTypeMismatchException(
                        completion.operationId(),
                        shift.machineId(),
                        machineTypeId,
                        operation.machineTypeId());
            }
        }

        shiftCompletionRepository.insertAll(machineShiftId, completions);
        machineShiftInstanceRepository.updateClosed(machineShiftId, "CLOSED", now);
    }

    private static void validateNoDuplicateOperationIds(List<ShiftCompletionRequest> completions) {
        Set<Long> uniqueOperationIds = new HashSet<>();
        for (ShiftCompletionRequest completion : completions) {
            if (!uniqueOperationIds.add(completion.operationId())) {
                throw new IllegalArgumentException(
                        "Duplicate operationId in request: " + completion.operationId());
            }
        }
    }
}
