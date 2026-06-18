package ru.ozbio.api.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record LinkMachinesToShiftRequest(@NotEmpty List<@NotNull Long> machineIds) {}
