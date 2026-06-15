package ru.ozbio.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ozbio.api.dto.CreateDetailRequest;
import ru.ozbio.api.dto.DetailResponse;
import ru.ozbio.api.dto.OperationResponse;
import ru.ozbio.persistence.DetailRepository;
import ru.ozbio.service.exception.DetailInUseException;
import ru.ozbio.service.exception.DetailNotFoundException;
import ru.ozbio.service.exception.InvalidReferenceException;
import ru.ozbio.service.model.CreateDetailCommand;
import ru.ozbio.service.model.DetailSummary;
import ru.ozbio.service.model.OperationLine;

@Service
public class DetailService {

    private final DetailRepository detailRepository;

    public DetailService(DetailRepository detailRepository) {
        this.detailRepository = detailRepository;
    }

    @Transactional
    public DetailResponse create(CreateDetailRequest request) {
        validateOperations(request);

        CreateDetailCommand command =
                new CreateDetailCommand(
                        request.name().trim(),
                        request.operations().stream()
                                .map(
                                        operation ->
                                                new CreateDetailCommand.Operation(
                                                        operation.step(),
                                                        operation.duration(),
                                                        operation.setupDuration(),
                                                        operation.machineTypeId()))
                                .toList());

        DetailSummary detail = detailRepository.insert(command);
        return toResponse(detail, detailRepository.findOperationsByDetailId(detail.id()));
    }

    @Transactional
    public void delete(long id) {
        if (!detailRepository.existsById(id)) {
            throw new DetailNotFoundException(id);
        }
        if (detailRepository.isReferenced(id)) {
            throw new DetailInUseException(id);
        }
        if (!detailRepository.deleteById(id)) {
            throw new DetailNotFoundException(id);
        }
    }

    private void validateOperations(CreateDetailRequest request) {
        Set<Integer> steps = new HashSet<>();
        for (var operation : request.operations()) {
            if (!steps.add(operation.step())) {
                throw new IllegalArgumentException("Duplicate operation step: " + operation.step());
            }
            if (!detailRepository.machineTypeExists(operation.machineTypeId())) {
                throw new InvalidReferenceException("machineTypeId", operation.machineTypeId());
            }
        }
    }

    private DetailResponse toResponse(DetailSummary detail, List<OperationLine> operations) {
        List<OperationResponse> operationResponses =
                operations.stream()
                        .map(
                                operation ->
                                        new OperationResponse(
                                                operation.id(),
                                                operation.step(),
                                                operation.duration(),
                                                operation.setupDuration(),
                                                operation.machineTypeId()))
                        .toList();
        return new DetailResponse(detail.id(), detail.name(), operationResponses);
    }
}
