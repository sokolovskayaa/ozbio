package ru.ozbio.service.exception;

public class DetailInUseException extends RuntimeException {

    private final long detailId;

    public DetailInUseException(long detailId) {
        super("Detail is referenced by orders or tools: " + detailId);
        this.detailId = detailId;
    }

    public long detailId() {
        return detailId;
    }
}
