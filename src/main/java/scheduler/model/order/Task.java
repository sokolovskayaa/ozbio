package scheduler.model.order;

import java.time.Duration;
import scheduler.model.machine.Capability;

public record Task(String taskId, int sequence, Duration duration, Capability requiredCapability) {}
