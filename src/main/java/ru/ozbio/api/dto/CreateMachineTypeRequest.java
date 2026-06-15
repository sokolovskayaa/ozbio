package ru.ozbio.api.dto;

import jakarta.validation.constraints.NotBlank;
import ru.ozbio.service.model.CreateMachineTypeCommand;

public record CreateMachineTypeRequest(@NotBlank String typeName) {

    public CreateMachineTypeCommand toCommand() {
        return new CreateMachineTypeCommand(typeName.trim());
    }
}
