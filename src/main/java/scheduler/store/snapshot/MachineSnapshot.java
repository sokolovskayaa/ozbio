package scheduler.store.snapshot;

import java.time.Instant;
import java.util.Set;
import scheduler.model.machine.Capability;
import scheduler.model.machine.Machine;
import scheduler.model.machine.MachineStatus;

public record MachineSnapshot(
        String machineId,
        String groupId,
        Set<Capability> capabilities,
        Instant availableAt,
        MachineStatus status) {

    public Machine toMachine(String defaultGroupId) {
        String gid = groupId != null && !groupId.isBlank() ? groupId : defaultGroupId;
        return new Machine(
                machineId,
                gid,
                capabilities,
                availableAt != null ? availableAt : Instant.EPOCH,
                status != null ? status : MachineStatus.IDLE);
    }

    public static MachineSnapshot from(Machine machine) {
        return new MachineSnapshot(
                machine.machineId(),
                machine.groupId(),
                machine.capabilities(),
                machine.availableAt(),
                machine.status());
    }
}
