package scheduler.store;

import java.time.DayOfWeek;
import java.time.LocalTime;
import scheduler.model.WorkWindow;

public class WorkWindowSnapshot {
    public String dayOfWeek;
    public LocalTime start;
    public LocalTime end;

    public WorkWindow toWindow() {
        return new WorkWindow(DayOfWeek.valueOf(dayOfWeek), start, end);
    }

    public static WorkWindowSnapshot from(WorkWindow window) {
        WorkWindowSnapshot dto = new WorkWindowSnapshot();
        dto.dayOfWeek = window.dayOfWeek().name();
        dto.start = window.start();
        dto.end = window.end();
        return dto;
    }
}
