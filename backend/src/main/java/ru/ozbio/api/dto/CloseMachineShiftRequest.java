package ru.ozbio.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CloseMachineShiftRequest(@NotNull List<@Valid ShiftCompletionRequest> completions) {}
