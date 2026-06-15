package ru.ozbio.api.dto;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;

public record OperationRequest(
        @NotNull Duration duration,
        @NotNull Long machineTypeId,
        Duration setupDuration) {

    public OperationRequest {
        if (setupDuration == null) {
            setupDuration = Duration.ZERO;
        }
    }
}
