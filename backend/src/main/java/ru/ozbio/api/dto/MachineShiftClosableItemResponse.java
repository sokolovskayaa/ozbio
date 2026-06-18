package ru.ozbio.api.dto;

import java.time.Instant;
import java.time.LocalDate;

public record MachineShiftClosableItemResponse(
        long id,
        long machineId,
        long shiftTypeId,
        LocalDate workDate,
        Instant windowStart,
        Instant windowEnd,
        String status,
        MachineShiftClosableShiftTypeResponse shiftType) {}
