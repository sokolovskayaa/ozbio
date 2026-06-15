package ru.ozbio.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateMachineTypeRequest(@NotBlank String typeName) {}
