package scheduler.api;

/** Счётчик выполненных операций при закрытии смены. {@code groupId} обязателен при {@code closeAllPendingShifts}. */
public record MachineTaskCountRequest(String machineId, String taskId, int completedCount, String groupId) {

    public MachineTaskCountRequest(String machineId, String taskId, int completedCount) {
        this(machineId, taskId, completedCount, null);
    }
}
