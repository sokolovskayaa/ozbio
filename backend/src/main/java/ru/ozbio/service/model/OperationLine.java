package ru.ozbio.service.model;

import java.time.Duration;

public record OperationLine(
        long id, int step, Duration duration, Duration setupDuration, long machineTypeId) {}
