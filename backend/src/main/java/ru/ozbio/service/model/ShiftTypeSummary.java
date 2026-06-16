package ru.ozbio.service.model;

import java.time.LocalTime;

public record ShiftTypeSummary(long id, int dayOfWeek, LocalTime startTime, LocalTime endTime) {}
