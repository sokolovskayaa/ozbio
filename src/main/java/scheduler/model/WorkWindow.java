package scheduler.model;

import java.time.DayOfWeek;
import java.time.LocalTime;

/** Интервал работы группы станков в течение недели. */
public record WorkWindow(DayOfWeek dayOfWeek, LocalTime start, LocalTime end) {
    public WorkWindow {
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Work window end must be after start: " + dayOfWeek);
        }
    }
}
