package ru.ozbio.api.dto;

public record OperationResponse(
        long id, int step, int duration, int setupDuration, long machineTypeId) {}
