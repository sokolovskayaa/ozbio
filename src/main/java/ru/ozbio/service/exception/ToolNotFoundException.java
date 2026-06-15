package ru.ozbio.service.exception;

public class ToolNotFoundException extends RuntimeException {

    private final long toolId;

    public ToolNotFoundException(long toolId) {
        super("Tool not found: " + toolId);
        this.toolId = toolId;
    }

    public long toolId() {
        return toolId;
    }
}
