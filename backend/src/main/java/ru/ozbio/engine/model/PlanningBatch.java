package ru.ozbio.engine.model;

public record PlanningBatch(long orderId, long operationId, int count, Long previousOperationId) {}
