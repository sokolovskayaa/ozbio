package ru.ozbio.service.exception;

public class MachineNotFoundException extends RuntimeException {

    private final long machineId;

    public MachineNotFoundException(long machineId) {
        super("Machine not found: " + machineId);
        this.machineId = machineId;
    }

    public long machineId() {
        return machineId;
    }
}
