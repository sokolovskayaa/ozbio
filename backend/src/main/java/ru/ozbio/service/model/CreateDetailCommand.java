package ru.ozbio.service.model;

import java.time.Duration;
import java.util.List;

public record CreateDetailCommand(String name, List<Operation> operations) {

    public record Operation(int step, Duration duration, Duration setupDuration, long machineTypeId) {}
}
