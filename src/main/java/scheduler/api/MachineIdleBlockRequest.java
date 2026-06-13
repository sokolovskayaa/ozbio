package scheduler.api;

import java.time.Instant;

public record MachineIdleBlockRequest(String machineId, Instant from, Instant to, String reason) {}
