package scheduler.api;

import scheduler.model.MachineStatus;

public record MachineStatusUpdateRequest(MachineStatus status) {}
