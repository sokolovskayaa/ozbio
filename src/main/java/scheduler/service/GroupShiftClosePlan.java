package scheduler.service;

import java.time.Instant;
import java.util.List;
import scheduler.api.ShiftOperationFactRequest;

/** Факты закрытия одной смены группы станков (без переплана). */
record GroupShiftClosePlan(String groupId, Instant shiftStart, Instant shiftEnd, List<ShiftOperationFactRequest> operations) {}
