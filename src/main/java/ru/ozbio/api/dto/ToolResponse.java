package ru.ozbio.api.dto;

import java.time.Duration;
import java.util.List;

public record ToolResponse(long id, String name, Duration assembleDuration, List<ToolDetailResponse> details) {}
