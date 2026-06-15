package ru.ozbio.service.exception;

public class InvalidReferenceException extends RuntimeException {

    private final String field;
    private final long id;

    public InvalidReferenceException(String field, long id) {
        super("Unknown " + field + ": " + id);
        this.field = field;
        this.id = id;
    }

    public String field() {
        return field;
    }

    public long id() {
        return id;
    }
}
