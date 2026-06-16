package ru.ozbio.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ozbio.api.dto.CreateDetailRequest;
import ru.ozbio.api.dto.DetailResponse;
import ru.ozbio.api.dto.OperationResponse;
import ru.ozbio.persistence.DetailRepository;
import ru.ozbio.service.exception.InvalidReferenceException;
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

        DetailSummary detail = detailRepository.insert(request.toCommand());
        return toResponse(detail, detailRepository.findOperationsByDetailId(detail.id()));
    }

    public List<DetailResponse> list() {
        List<DetailSummary> details = detailRepository.findAll();
        var operationsByDetailId =
                detailRepository.findOperationsByDetailIds(
                        details.stream().map(DetailSummary::id).toList());

        return details.stream()
                .map(
                        detail ->
                                toResponse(
                                        detail,
                                        operationsByDetailId.getOrDefault(detail.id(), List.of())))
                .toList();
    }

    @Transactional
    public void delete(long id) {
        detailRepository.deleteById(id);
    }

    private void validateOperations(CreateDetailRequest request) {
        for (var operation : request.operations()) {
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
