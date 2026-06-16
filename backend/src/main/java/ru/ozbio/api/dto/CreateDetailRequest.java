package ru.ozbio.api.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import ru.ozbio.service.model.CreateDetailCommand;

public record CreateDetailRequest(
        @NotBlank String name, @NotEmpty @Valid List<OperationRequest> operations) {

    public CreateDetailCommand toCommand() {
        List<CreateDetailCommand.Operation> mappedOperations = new ArrayList<>();
        int step = 1;
        for (OperationRequest operation : operations) {
            mappedOperations.add(
                    new CreateDetailCommand.Operation(
                            step++,
                            operation.duration(),
                            operation.setupDuration(),
                            operation.machineTypeId()));
        }
        return new CreateDetailCommand(name.trim(), mappedOperations);
    }
}
