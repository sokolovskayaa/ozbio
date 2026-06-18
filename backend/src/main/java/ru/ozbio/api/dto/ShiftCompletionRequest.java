package ru.ozbio.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ShiftCompletionRequest(@NotNull Long operationId, @Min(1) int count) {}
