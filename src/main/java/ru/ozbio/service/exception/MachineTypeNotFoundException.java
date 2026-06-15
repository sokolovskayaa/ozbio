package ru.ozbio.service.exception;

public class MachineTypeNotFoundException extends RuntimeException {

    private final long machineTypeId;

    public MachineTypeNotFoundException(long machineTypeId) {
        super("Machine type not found: " + machineTypeId);
        this.machineTypeId = machineTypeId;
    }

    public long machineTypeId() {
        return machineTypeId;
    }
}
