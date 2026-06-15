package ru.ozbio.api.dto;

import java.util.List;

import jakarta.validation.Valid;

public record CreateOrderRequest(@Valid List<OrderDetailRequest> details, @Valid List<OrderToolRequest> tools) {

    public CreateOrderRequest {
        if (details == null) {
            details = List.of();
        }
        if (tools == null) {
            tools = List.of();
        }
    }
}
