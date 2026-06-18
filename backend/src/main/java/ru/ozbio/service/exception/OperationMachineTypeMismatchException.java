package ru.ozbio.service.exception;

public class OperationMachineTypeMismatchException extends RuntimeException {

    private final long operationId;
    private final long machineId;
    private final long machineTypeId;
    private final long operationMachineTypeId;

    public OperationMachineTypeMismatchException(
            long operationId, long machineId, long machineTypeId, long operationMachineTypeId) {
        super(
                "Operation "
                        + operationId
                        + " cannot be performed on machine "
                        + machineId
                        + " (machine type mismatch)");
        this.operationId = operationId;
        this.machineId = machineId;
        this.machineTypeId = machineTypeId;
        this.operationMachineTypeId = operationMachineTypeId;
    }

    public long operationId() {
        return operationId;
    }

    public long machineId() {
        return machineId;
    }

    public long machineTypeId() {
        return machineTypeId;
    }

    public long operationMachineTypeId() {
        return operationMachineTypeId;
    }
}
