package ru.ozbio.service.model;

import java.time.LocalTime;

public record CreateShiftTypeCommand(int dayOfWeek, LocalTime startTime, LocalTime endTime) {}
