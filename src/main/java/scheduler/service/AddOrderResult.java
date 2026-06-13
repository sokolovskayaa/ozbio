package scheduler.service;

import java.time.Instant;
import java.util.List;
import scheduler.model.schedule.Assignment;

public record AddOrderResult(String orderId, Instant readyAt, List<Assignment> assignmentsForOrder) {}
