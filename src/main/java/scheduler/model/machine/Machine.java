package scheduler.model.machine;

import java.time.Instant;
import java.util.Set;

public final class Machine {
    private final String machineId;
    private final String groupId;
    private final Set<Capability> capabilities;
    private Instant availableAt;
    private MachineStatus status;

    public Machine(
            String machineId,
            String groupId,
            Set<Capability> capabilities,
            Instant availableAt,
            MachineStatus status) {
        this.machineId = machineId;
        this.groupId = groupId;
        this.capabilities = Set.copyOf(capabilities);
        this.availableAt = availableAt;
        this.status = status;
    }

    public String groupId() {
        return groupId;
    }

    public String machineId() {
        return machineId;
    }

    public Set<Capability> capabilities() {
        return capabilities;
    }

    public Instant availableAt() {
        return availableAt;
    }

    public MachineStatus status() {
        return status;
    }

    public void setAvailableAt(Instant availableAt) {
        this.availableAt = availableAt;
    }

    public void setStatus(MachineStatus status) {
        this.status = status;
    }

    public boolean canPerform(Capability capability) {
        return capabilities.contains(capability);
    }

    /** Станок принимает новые задачи в планировщике. */
    public boolean isOperational() {
        return status != MachineStatus.DOWN && status != MachineStatus.MAINTENANCE && status != MachineStatus.SETUP;
    }
}
