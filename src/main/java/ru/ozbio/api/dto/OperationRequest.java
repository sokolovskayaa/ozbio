package ru.ozbio.api.dto;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OperationRequest(
        @NotNull @Min(1) Integer step,
        @NotNull Duration duration,
        @NotNull Long machineTypeId,
        Duration setupDuration) {

    public OperationRequest {
        if (setupDuration == null) {
            setupDuration = Duration.ZERO;
        }
    }
}
