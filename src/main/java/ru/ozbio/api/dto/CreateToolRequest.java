package ru.ozbio.api.dto;

import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CreateToolRequest(
        @NotBlank String name,
        @NotNull Duration assembleDuration,
        @NotEmpty @Valid List<ToolDetailRequest> details) {}
