package scheduler.model;

import java.time.Instant;

/** Простой или иная блокировка станка в календаре (не операция заказа). */
public record MachineBlock(String machineId, Instant from, Instant to, String reason) {
    public MachineBlock {
        if (machineId == null || machineId.isBlank()) {
            throw new IllegalArgumentException("machineId required");
        }
        if (from == null || to == null || !to.isAfter(from)) {
            throw new IllegalArgumentException("block interval must have to > from");
        }
    }
}
