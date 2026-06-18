package ru.ozbio.api.dto;

import java.time.LocalTime;

public record MachineShiftClosableShiftTypeResponse(
        int dayOfWeek, LocalTime startTime, LocalTime endTime) {}
