package scheduler.model;

import java.time.Duration;

public record Task(String taskId, int sequence, Duration duration, Capability requiredCapability) {}
