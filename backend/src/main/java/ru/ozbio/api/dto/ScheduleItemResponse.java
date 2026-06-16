package ru.ozbio.api.dto;

import java.time.Instant;

public record ScheduleItemResponse(
        long orderId,
        long operationId,
        long machineId,
        int count,
        Instant start,
        Instant end) {}
