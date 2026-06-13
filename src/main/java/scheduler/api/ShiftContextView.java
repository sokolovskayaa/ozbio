package scheduler.api;

import java.time.Instant;
import java.util.List;

public record ShiftContextView(
        boolean stale,
        int pendingShiftCount,
        List<ShiftInfoView> pendingShifts,
        ShiftInfoView activeShift,
        List<ShiftCloseRowView> closeRows) {

    /** Строка формы: один тип операции на станке в окне смены группы. */
    public record ShiftCloseRowView(
            String groupId,
            String groupName,
            Instant shiftStart,
            Instant shiftEnd,
            String machineId,
            String machineTitle,
            String taskId,
            String taskTitle,
            int plannedCount,
            int defaultCompletedCount) {}

    public record ShiftInfoView(
            String groupId,
            String groupName,
            Instant shiftStart,
            Instant shiftEnd,
            boolean overdue,
            List<MachineShiftRowView> machines) {}

    public record MachineShiftRowView(
            String machineId,
            String machineTitle,
            List<OperationCountView> operations) {}

    public record OperationCountView(
            String taskId, String taskTitle, int plannedCount, int defaultCompletedCount) {}
}
