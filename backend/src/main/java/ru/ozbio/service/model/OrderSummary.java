package ru.ozbio.service.model;

import java.time.Instant;

import ru.ozbio.domain.OrderStatus;

public record OrderSummary(long id, OrderStatus status, Instant createdAt) {}
