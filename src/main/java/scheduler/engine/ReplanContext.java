package scheduler.engine;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Контекст перепланирования: предпочтительный станок для операции. */
public final class ReplanContext {
    private final Map<WorkKey, String> preferredMachineByWork = new HashMap<>();

    public record WorkKey(String orderId, String partId, int unitIndex, String taskId) {}

    public void rememberMachine(String orderId, String partId, int unitIndex, String taskId, String machineId) {
        preferredMachineByWork.put(new WorkKey(orderId, partId, unitIndex, taskId), machineId);
    }

    public Optional<String> preferredMachine(String orderId, String partId, int unitIndex, String taskId) {
        return Optional.ofNullable(preferredMachineByWork.get(new WorkKey(orderId, partId, unitIndex, taskId)));
    }
}
