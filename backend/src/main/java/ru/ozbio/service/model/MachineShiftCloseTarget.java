package ru.ozbio.service.model;

import java.time.Instant;

public record MachineShiftCloseTarget(long id, long machineId, Instant windowStart, String status) {}
