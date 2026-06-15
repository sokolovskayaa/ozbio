package ru.ozbio.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderDetailRequest(@NotNull Long detailId, @NotNull @Min(1) Integer count) {}
