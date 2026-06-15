package ru.ozbio.service.exception;

public class ToolInUseException extends RuntimeException {

    private final long toolId;

    public ToolInUseException(long toolId) {
        super("Tool is referenced by orders: " + toolId);
        this.toolId = toolId;
    }

    public long toolId() {
        return toolId;
    }
}
