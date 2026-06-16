package ru.ozbio.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OperationRequest(
        @Min(1) int duration, @NotNull Long machineTypeId, Integer setupDuration) {

    public OperationRequest {
        if (setupDuration == null) {
            setupDuration = 0;
        } else if (setupDuration < 0) {
            throw new IllegalArgumentException("setupDuration must be non-negative");
        }
    }
}
