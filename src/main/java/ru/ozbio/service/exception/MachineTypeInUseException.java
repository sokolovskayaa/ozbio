package ru.ozbio.service.exception;

public class MachineTypeInUseException extends RuntimeException {

    private final long machineTypeId;

    public MachineTypeInUseException(long machineTypeId) {
        super("Machine type is referenced by machines or operations: " + machineTypeId);
        this.machineTypeId = machineTypeId;
    }

    public long machineTypeId() {
        return machineTypeId;
    }
}
