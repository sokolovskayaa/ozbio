package ru.ozbio.api.dto;

import jakarta.validation.constraints.NotNull;

public record CreateMachineRequest(@NotNull Long machineTypeId) {}
