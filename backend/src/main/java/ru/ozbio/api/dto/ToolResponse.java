package ru.ozbio.api.dto;

import java.util.List;

public record ToolResponse(long id, String name, int assembleDuration, List<ToolDetailResponse> details) {}
