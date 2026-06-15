package ru.ozbio.service.model;

import java.util.List;

public record CreateOrderCommand(List<DetailLine> details, List<ToolLine> tools) {

    public record DetailLine(long detailId, int count) {}

    public record ToolLine(long toolId, int count) {}
}
