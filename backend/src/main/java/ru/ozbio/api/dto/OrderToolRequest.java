package ru.ozbio.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderToolRequest(@NotNull Long toolId, @NotNull @Min(1) Integer count) {}
