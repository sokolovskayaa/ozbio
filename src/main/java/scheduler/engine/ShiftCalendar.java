package scheduler.engine;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import scheduler.model.MachineGroup;
import scheduler.model.WorkWindow;

/** Размещение работ в рамках смен группы станков. */
public final class ShiftCalendar {
    private static final int MAX_SCAN_DAYS = 14;

    private ShiftCalendar() {}

    public static Instant nextWorkStart(Instant instant, MachineGroup group, ZoneId zone) {
        if (group == null || group.workWindows().isEmpty()) {
            return instant;
        }
        ZonedDateTime cursor = instant.atZone(zone);
        for (int day = 0; day < MAX_SCAN_DAYS; day++) {
            LocalDate date = cursor.toLocalDate().plusDays(day);
            List<WorkWindow> dayWindows = group.workWindows().stream()
                    .filter(w -> w.dayOfWeek().equals(date.getDayOfWeek()))
                    .sorted(Comparator.comparing(WorkWindow::start))
                    .toList();
            for (WorkWindow window : dayWindows) {
                ZonedDateTime windowStart = ZonedDateTime.of(date, window.start(), zone);
                ZonedDateTime windowEnd = ZonedDateTime.of(date, window.end(), zone);
                if (!windowEnd.isAfter(cursor)) {
                    continue;
                }
                if (!cursor.isBefore(windowStart) && cursor.isBefore(windowEnd)) {
                    return cursor.toInstant();
                }
                if (windowStart.isAfter(cursor)) {
                    return windowStart.toInstant();
                }
            }
        }
        return instant;
    }

    /**
     * Можно ли выполнить {@code duration} рабочих минут, начиная в {@code start}, не выходя за конец
     * текущей смены (одного окна {@link WorkWindow}).
     */
    public static boolean fitsInSingleShift(Instant start, Duration duration, MachineGroup group, ZoneId zone) {
        if (duration.isZero()) {
            return true;
        }
        if (group == null || group.workWindows().isEmpty()) {
            return true;
        }
        Instant workStart = nextWorkStart(start, group, zone);
        Slot slot = currentSlot(workStart, group, zone);
        if (slot == null) {
            return false;
        }
        Instant end = addWorkDuration(workStart, duration, group, zone);
        return !end.isAfter(slot.end);
    }

    /**
     * Ближайший допустимый старт не раньше {@code earliest}, при котором вся операция укладывается
     * в одну смену.
     */
    public static Optional<Instant> nextShiftStartFittingDuration(
            Instant earliest, Duration duration, MachineGroup group, ZoneId zone) {
        if (group == null || group.workWindows().isEmpty()) {
            return Optional.of(nextWorkStart(earliest, group, zone));
        }
        Instant cursor = earliest;
        for (int guard = 0; guard < 500; guard++) {
            Instant candidate = nextWorkStart(cursor, group, zone);
            if (fitsInSingleShift(candidate, duration, group, zone)) {
                return Optional.of(candidate);
            }
            Instant next = nextShiftAfter(candidate, group, zone);
            if (!next.isAfter(cursor)) {
                cursor = cursor.plusSeconds(60);
            } else {
                cursor = next;
            }
        }
        return Optional.empty();
    }

    /** Окно смены (начало и конец {@link WorkWindow}), в которое попадает {@code instant}. */
    public static Optional<ShiftWindow> shiftWindowContaining(Instant instant, MachineGroup group, ZoneId zone) {
        if (group == null || group.workWindows().isEmpty()) {
            return Optional.of(new ShiftWindow(instant, instant));
        }
        ZonedDateTime zdt = instant.atZone(zone);
        LocalDate date = zdt.toLocalDate();
        for (WorkWindow window : group.workWindows()) {
            if (!window.dayOfWeek().equals(date.getDayOfWeek())) {
                continue;
            }
            ZonedDateTime windowStart = ZonedDateTime.of(date, window.start(), zone);
            ZonedDateTime windowEnd = ZonedDateTime.of(date, window.end(), zone);
            if (!zdt.isBefore(windowStart) && zdt.isBefore(windowEnd)) {
                return Optional.of(new ShiftWindow(windowStart.toInstant(), windowEnd.toInstant()));
            }
        }
        return Optional.empty();
    }

    /**
     * Смены группы, уже завершившиеся к {@code now}, но ещё не отмеченные закрытыми
     * (конец смены строго после {@code afterExclusiveEnd}).
     */
    public static List<ShiftWindow> shiftWindowsEndingAfter(
            Instant afterExclusiveEnd, Instant now, MachineGroup group, ZoneId zone) {
        List<ShiftWindow> result = new ArrayList<>();
        if (group == null || group.workWindows().isEmpty()) {
            return result;
        }
        Instant cursor = afterExclusiveEnd;
        for (int guard = 0; guard < 500; guard++) {
            Instant at = nextWorkStart(cursor, group, zone);
            if (!at.isAfter(cursor)) {
                at = nextWorkStart(cursor.plusSeconds(60), group, zone);
            }
            Optional<ShiftWindow> window = shiftWindowContaining(at, group, zone);
            if (window.isEmpty()) {
                break;
            }
            ShiftWindow w = window.get();
            if (!w.end().isAfter(afterExclusiveEnd)) {
                cursor = w.end();
                continue;
            }
            if (w.end().isAfter(now)) {
                break;
            }
            result.add(w);
            cursor = w.end();
        }
        return List.copyOf(result);
    }

    public record ShiftWindow(Instant start, Instant end) {}

    /** Конец текущей смены для {@code instant} (внутри окна), иначе {@code instant}. */
    public static Instant currentShiftEnd(Instant instant, MachineGroup group, ZoneId zone) {
        if (group == null || group.workWindows().isEmpty()) {
            return instant;
        }
        Instant workStart = nextWorkStart(instant, group, zone);
        Slot slot = currentSlot(workStart, group, zone);
        return slot != null ? slot.end : workStart;
    }

    private static Instant nextShiftAfter(Instant instant, MachineGroup group, ZoneId zone) {
        Instant workStart = nextWorkStart(instant, group, zone);
        Slot slot = currentSlot(workStart, group, zone);
        if (slot == null) {
            return nextWorkStart(instant.plusSeconds(60), group, zone);
        }
        return nextWorkStart(slot.end, group, zone);
    }

    public static Instant addWorkDuration(
            Instant start, Duration duration, MachineGroup group, ZoneId zone) {
        if (duration.isZero()) {
            return start;
        }
        if (group == null || group.workWindows().isEmpty()) {
            return start.plus(duration);
        }
        Instant cursor = nextWorkStart(start, group, zone);
        Duration remaining = duration;
        for (int guard = 0; guard < 500 && remaining.compareTo(Duration.ZERO) > 0; guard++) {
            Slot slot = currentSlot(cursor, group, zone);
            if (slot == null) {
                cursor = nextWorkStart(cursor.plusSeconds(60), group, zone);
                continue;
            }
            Duration available = Duration.between(cursor, slot.end);
            if (available.compareTo(remaining) >= 0) {
                return cursor.plus(remaining);
            }
            remaining = remaining.minus(available);
            cursor = nextWorkStart(slot.end, group, zone);
        }
        return cursor.plus(remaining);
    }

    /** Момент за {@code duration} рабочих минут до {@code end} (в пределах смен). */
    public static Instant subtractWorkDuration(
            Instant end, Duration duration, MachineGroup group, ZoneId zone) {
        if (duration.isZero()) {
            return end;
        }
        if (group == null || group.workWindows().isEmpty()) {
            return end.minus(duration);
        }
        Instant cursor = end;
        Duration remaining = duration;
        for (int guard = 0; guard < 500 && remaining.compareTo(Duration.ZERO) > 0; guard++) {
            Slot slot = currentSlot(cursor, group, zone);
            if (slot == null) {
                cursor = previousWorkInstant(cursor, group, zone);
                if (cursor.equals(end)) {
                    return end.minus(remaining);
                }
                continue;
            }
            Duration available = Duration.between(slot.start, cursor);
            if (!cursor.equals(slot.end) && available.compareTo(Duration.ZERO) == 0) {
                available = Duration.between(slot.start, slot.end);
                cursor = slot.end;
            }
            if (available.compareTo(remaining) >= 0) {
                return cursor.minus(remaining);
            }
            remaining = remaining.minus(available);
            cursor = slot.start;
            if (cursor.equals(slot.start)) {
                cursor = previousWorkInstant(slot.start, group, zone);
            }
        }
        return end.minus(duration);
    }

    private static Instant previousWorkInstant(Instant instant, MachineGroup group, ZoneId zone) {
        ZonedDateTime zdt = instant.atZone(zone).minusSeconds(1);
        for (int day = 0; day < MAX_SCAN_DAYS; day++) {
            LocalDate date = zdt.toLocalDate().minusDays(day);
            List<WorkWindow> dayWindows = group.workWindows().stream()
                    .filter(w -> w.dayOfWeek().equals(date.getDayOfWeek()))
                    .sorted(Comparator.comparing(WorkWindow::start).reversed())
                    .toList();
            for (WorkWindow window : dayWindows) {
                ZonedDateTime windowEnd = ZonedDateTime.of(date, window.end(), zone);
                ZonedDateTime windowStart = ZonedDateTime.of(date, window.start(), zone);
                if (windowEnd.isAfter(zdt) && !windowStart.isAfter(zdt)) {
                    return windowEnd.toInstant();
                }
                if (windowEnd.isBefore(zdt) || windowEnd.equals(zdt)) {
                    return windowEnd.toInstant();
                }
            }
        }
        return instant.minusSeconds(60);
    }

    /** Фактические интервалы работы в смене (без ночи/выходных между ними). */
    public static List<TimeSegment> workSegments(
            Instant start, Duration workDuration, MachineGroup group, ZoneId zone) {
        if (workDuration.isZero()) {
            return List.of(new TimeSegment(start, start));
        }
        if (group == null || group.workWindows().isEmpty()) {
            return List.of(new TimeSegment(start, start.plus(workDuration)));
        }
        List<TimeSegment> segments = new ArrayList<>();
        Instant cursor = nextWorkStart(start, group, zone);
        Duration remaining = workDuration;
        for (int guard = 0; guard < 500 && remaining.compareTo(Duration.ZERO) > 0; guard++) {
            Slot slot = currentSlot(cursor, group, zone);
            if (slot == null) {
                cursor = nextWorkStart(cursor.plusSeconds(60), group, zone);
                continue;
            }
            Duration available = Duration.between(cursor, slot.end);
            if (available.compareTo(remaining) >= 0) {
                segments.add(new TimeSegment(cursor, cursor.plus(remaining)));
                remaining = Duration.ZERO;
            } else {
                segments.add(new TimeSegment(cursor, slot.end));
                remaining = remaining.minus(available);
                cursor = nextWorkStart(slot.end, group, zone);
            }
        }
        if (remaining.compareTo(Duration.ZERO) > 0) {
            segments.add(new TimeSegment(cursor, cursor.plus(remaining)));
        }
        return List.copyOf(segments);
    }

    public record TimeSegment(Instant start, Instant end) {}

    private static Slot currentSlot(Instant instant, MachineGroup group, ZoneId zone) {
        ZonedDateTime zdt = instant.atZone(zone);
        LocalDate date = zdt.toLocalDate();
        for (WorkWindow window : group.workWindows()) {
            if (!window.dayOfWeek().equals(date.getDayOfWeek())) {
                continue;
            }
            ZonedDateTime windowStart = ZonedDateTime.of(date, window.start(), zone);
            ZonedDateTime windowEnd = ZonedDateTime.of(date, window.end(), zone);
            if (!zdt.isBefore(windowStart) && zdt.isBefore(windowEnd)) {
                return new Slot(instant, windowEnd.toInstant());
            }
        }
        return null;
    }

    private record Slot(Instant start, Instant end) {}
}
