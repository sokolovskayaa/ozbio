package ru.ozbio.engine.model;

import java.time.Instant;

public record PlannedScheduleItem(
        long orderId,
        long operationId,
        long machineId,
        int count,
        Instant start,
        Instant end) {}
