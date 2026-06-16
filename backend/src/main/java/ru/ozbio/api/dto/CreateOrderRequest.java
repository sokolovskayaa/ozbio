package ru.ozbio.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import ru.ozbio.service.model.CreateOrderCommand;

public record CreateOrderRequest(@Valid List<OrderDetailRequest> details, @Valid List<OrderToolRequest> tools) {

    public CreateOrderRequest {
        if (details == null) {
            details = List.of();
        }
        if (tools == null) {
            tools = List.of();
        }
    }

    public CreateOrderCommand toCommand() {
        return new CreateOrderCommand(
                details.stream()
                        .map(detail -> new CreateOrderCommand.DetailLine(detail.detailId(), detail.count()))
                        .toList(),
                tools.stream()
                        .map(tool -> new CreateOrderCommand.ToolLine(tool.toolId(), tool.count()))
                        .toList());
    }
}
