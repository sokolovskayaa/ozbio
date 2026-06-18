package ru.ozbio.service.model;

import java.time.LocalDate;

public record MachineShiftKey(long machineId, long shiftTypeId, LocalDate workDate) {}
