package ru.ozbio.engine.shift;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShiftWindowCalculatorTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");

    private final ShiftWindowCalculator calculator = new ShiftWindowCalculator();

    @Test
    void calculate_dayShift_sameCalendarDay() {
        LocalDate workDate = LocalDate.of(2026, 5, 22);

        var window = calculator.calculate(workDate, LocalTime.of(8, 0), LocalTime.of(16, 0), ZONE);

        assertThat(window.start()).isEqualTo(Instant.parse("2026-05-22T05:00:00Z"));
        assertThat(window.end()).isEqualTo(Instant.parse("2026-05-22T13:00:00Z"));
    }

    @Test
    void calculate_nightShift_endOnNextDay() {
        LocalDate workDate = LocalDate.of(2026, 5, 22);

        var window = calculator.calculate(workDate, LocalTime.of(22, 0), LocalTime.of(6, 0), ZONE);

        assertThat(window.start()).isEqualTo(Instant.parse("2026-05-22T19:00:00Z"));
        assertThat(window.end()).isEqualTo(Instant.parse("2026-05-23T03:00:00Z"));
    }

    @Test
    void calculate_rejectsZeroLengthWindow() {
        LocalDate workDate = LocalDate.of(2026, 5, 22);

        assertThatThrownBy(
                        () -> calculator.calculate(workDate, LocalTime.of(8, 0), LocalTime.of(8, 0), ZONE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
