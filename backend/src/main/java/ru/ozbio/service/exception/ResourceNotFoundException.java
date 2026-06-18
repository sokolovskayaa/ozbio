package ru.ozbio.service.exception;

public class ResourceNotFoundException extends RuntimeException {

    private final String field;
    private final long id;

    public ResourceNotFoundException(String field, long id) {
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
