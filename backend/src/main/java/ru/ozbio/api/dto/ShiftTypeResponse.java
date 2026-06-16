package ru.ozbio.api.dto;

import java.time.LocalTime;

public record ShiftTypeResponse(long id, int dayOfWeek, LocalTime startTime, LocalTime endTime) {}
