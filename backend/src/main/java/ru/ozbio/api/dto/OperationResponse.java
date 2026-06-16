package ru.ozbio.api.dto;

import java.time.Duration;

public record OperationResponse(
        long id, int step, Duration duration, Duration setupDuration, long machineTypeId) {}
