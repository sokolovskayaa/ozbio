package ru.ozbio.service.exception;

public class DetailNotFoundException extends RuntimeException {

    private final long detailId;

    public DetailNotFoundException(long detailId) {
        super("Detail not found: " + detailId);
        this.detailId = detailId;
    }

    public long detailId() {
        return detailId;
    }
}
