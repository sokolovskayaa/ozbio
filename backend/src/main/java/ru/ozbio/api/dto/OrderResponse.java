package ru.ozbio.api.dto;

import java.time.Instant;
import java.util.List;

import ru.ozbio.domain.OrderStatus;

public record OrderResponse(
        long id, OrderStatus status, Instant createdAt, List<OrderDetailResponse> details, List<OrderToolResponse> tools) {}
