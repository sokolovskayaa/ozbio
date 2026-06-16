package ru.ozbio.engine.exception;

public class NoMachineAvailableException extends RuntimeException {

    private final long machineTypeId;
    private final long operationId;

    public NoMachineAvailableException(long machineTypeId, long operationId) {
        super(
                "No machine available for machine type "
                        + machineTypeId
                        + " (operation "
                        + operationId
                        + ")");
        this.machineTypeId = machineTypeId;
        this.operationId = operationId;
    }

    public long machineTypeId() {
        return machineTypeId;
    }

    public long operationId() {
        return operationId;
    }
}
