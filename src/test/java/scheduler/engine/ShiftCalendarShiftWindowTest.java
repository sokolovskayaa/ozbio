package scheduler.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import scheduler.model.MachineGroup;
import scheduler.model.WorkWindow;

class ShiftCalendarShiftWindowTest {
    @Test
    void shiftWindowsEndingAfter_listsCompletedShiftsSinceLastClose() {
        MachineGroup group = new MachineGroup(
                "test",
                "test",
                List.of(
                        new WorkWindow(DayOfWeek.FRIDAY, LocalTime.of(8, 0), LocalTime.of(12, 0)),
                        new WorkWindow(DayOfWeek.FRIDAY, LocalTime.of(13, 0), LocalTime.of(17, 0))),
                java.time.Duration.ZERO);
        Instant after = Instant.parse("2026-05-22T05:00:00Z");
        Instant now = Instant.parse("2026-05-22T15:00:00Z");

        var windows = ShiftCalendar.shiftWindowsEndingAfter(after, now, group, FactoryZone.ZONE);

        assertEquals(2, windows.size());
        assertTrue(windows.getFirst().end().isBefore(windows.get(1).end()));
    }
}
