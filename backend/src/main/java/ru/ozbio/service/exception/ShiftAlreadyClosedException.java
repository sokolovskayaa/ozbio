package ru.ozbio.service.exception;

public class ShiftAlreadyClosedException extends RuntimeException {

    private final long machineShiftId;

    public ShiftAlreadyClosedException(long machineShiftId) {
        super("Machine shift is already closed: " + machineShiftId);
        this.machineShiftId = machineShiftId;
    }

    public long machineShiftId() {
        return machineShiftId;
    }
}
