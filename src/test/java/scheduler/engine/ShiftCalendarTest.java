package scheduler.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import scheduler.model.MachineGroup;
import scheduler.model.WorkWindow;

class ShiftCalendarTest {
    private static final ZoneId ZONE = FactoryZone.ZONE;

    @Test
    void addWorkDuration_staysInsideSameDayWindow() {
        MachineGroup group = new MachineGroup(
                "test",
                "test",
                List.of(new WorkWindow(DayOfWeek.FRIDAY, LocalTime.of(8, 0), LocalTime.of(20, 0))),
                Duration.ZERO);
        Instant start = Instant.parse("2026-05-22T08:00:00Z");
        Instant end = ShiftCalendar.addWorkDuration(start, Duration.ofMinutes(90), group, ZONE);
        assertEquals(start.plus(Duration.ofMinutes(90)), end);
    }

    @Test
    void addWorkDuration_spillsToNextWorkDay() {
        MachineGroup group = new MachineGroup(
                "test",
                "test",
                List.of(
                        new WorkWindow(DayOfWeek.FRIDAY, LocalTime.of(8, 0), LocalTime.of(12, 0)),
                        new WorkWindow(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))),
                Duration.ZERO);
        Instant start = Instant.parse("2026-05-22T05:00:00Z");
        Instant end = ShiftCalendar.addWorkDuration(start, Duration.ofHours(5), group, ZONE);
        assertEquals(Instant.parse("2026-05-25T06:00:00Z"), end);
    }

    @Test
    void fitsInSingleShift_falseWhenWorkWouldCrossShiftEnd() {
        MachineGroup group = new MachineGroup(
                "test",
                "test",
                List.of(new WorkWindow(DayOfWeek.FRIDAY, LocalTime.of(8, 0), LocalTime.of(20, 0))),
                Duration.ZERO);
        Instant almostEndOfShift =
                ZonedDateTime.of(2026, 5, 22, 19, 30, 0, 0, ZONE).toInstant();
        assertFalse(ShiftCalendar.fitsInSingleShift(
                almostEndOfShift, Duration.ofMinutes(70), group, ZONE));
    }

    @Test
    void nextShiftStartFittingDuration_skipsToNextWorkDay() {
        MachineGroup group = new MachineGroup(
                "test",
                "test",
                List.of(
                        new WorkWindow(DayOfWeek.FRIDAY, LocalTime.of(8, 0), LocalTime.of(20, 0)),
                        new WorkWindow(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(20, 0))),
                Duration.ZERO);
        Instant almostEndOfShift =
                ZonedDateTime.of(2026, 5, 22, 19, 30, 0, 0, ZONE).toInstant();
        Instant expected =
                ZonedDateTime.of(2026, 5, 25, 8, 0, 0, 0, ZONE).toInstant();
        assertEquals(
                expected,
                ShiftCalendar.nextShiftStartFittingDuration(
                                almostEndOfShift, Duration.ofMinutes(70), group, ZONE)
                        .orElseThrow());
    }

    @Test
    void workSegments_splitsAcrossNonWorkingGap() {
        MachineGroup group = new MachineGroup(
                "test",
                "test",
                List.of(new WorkWindow(DayOfWeek.FRIDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))),
                Duration.ZERO);
        Instant start = Instant.parse("2026-05-22T08:00:00Z");
        var segments = ShiftCalendar.workSegments(start, Duration.ofHours(3), group, ZONE);
        assertEquals(2, segments.size());
        assertTrue(segments.get(0).end().isBefore(segments.get(1).start()));
    }
}
