package scheduler.api;

import java.time.Instant;

public record TimeUpdateRequest(Instant currentTime) {}
