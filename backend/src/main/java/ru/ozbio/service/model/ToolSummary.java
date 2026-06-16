package ru.ozbio.service.model;

import java.time.Duration;

public record ToolSummary(long id, String name, Duration assembleDuration) {}
