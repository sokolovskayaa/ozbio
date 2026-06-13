package scheduler.api;

import java.time.Instant;
import java.util.List;

public record ShiftCloseRequest(
        Instant shiftEnd,
        String groupId,
        Instant shiftStart,
        List<ShiftOperationFactRequest> operations,
        List<MachineTaskCountRequest> machineTaskCounts,
        List<MachineIdleBlockRequest> idleBlocks,
        Boolean closeAllPendingShifts) {

    public ShiftCloseRequest(
            Instant shiftEnd,
            List<ShiftOperationFactRequest> operations,
            List<MachineIdleBlockRequest> idleBlocks) {
        this(shiftEnd, null, null, operations, null, idleBlocks, false);
    }

    public ShiftCloseRequest(
            Instant shiftEnd,
            String groupId,
            Instant shiftStart,
            List<ShiftOperationFactRequest> operations,
            List<MachineTaskCountRequest> machineTaskCounts,
            List<MachineIdleBlockRequest> idleBlocks) {
        this(shiftEnd, groupId, shiftStart, operations, machineTaskCounts, idleBlocks, false);
    }
}
