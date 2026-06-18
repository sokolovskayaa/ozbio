package ru.ozbio.engine.shift;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

@Component
public class ShiftWindowCalculator {

    public ShiftWindow calculate(LocalDate workDate, LocalTime startTime, LocalTime endTime, ZoneId zoneId) {
        var windowStart = workDate.atTime(startTime).atZone(zoneId);
        LocalDate endDate = endTime.isBefore(startTime) ? workDate.plusDays(1) : workDate;
        var windowEnd = endDate.atTime(endTime).atZone(zoneId);
        if (!windowEnd.isAfter(windowStart)) {
            throw new IllegalArgumentException("Shift window end must be after start");
        }
        return new ShiftWindow(windowStart.toInstant(), windowEnd.toInstant());
    }

    public record ShiftWindow(Instant start, Instant end) {}
}
