package ru.ozbio.service.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record MachineShiftClosableSummary(
        long id,
        long machineId,
        long shiftTypeId,
        LocalDate workDate,
        Instant windowStart,
        Instant windowEnd,
        String status,
        int shiftTypeDayOfWeek,
        LocalTime shiftTypeStartTime,
        LocalTime shiftTypeEndTime) {}
