package ru.ozbio.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.ozbio.config.ShiftGenerationProperties;
import ru.ozbio.engine.shift.ShiftWindowCalculator;
import ru.ozbio.persistence.MachineShiftCalendarRepository;
import ru.ozbio.persistence.MachineShiftRepository;
import ru.ozbio.persistence.ShiftRepository;
import ru.ozbio.service.model.MachineShiftKey;
import ru.ozbio.service.model.MachineShiftTypeLink;
import ru.ozbio.service.model.ShiftGenerationResult;
import ru.ozbio.service.model.ShiftTypeSummary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShiftGenerationServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");

    @Mock
    ShiftGenerationProperties properties;

    @Mock
    ShiftRepository shiftRepository;

    @Mock
    MachineShiftRepository machineShiftRepository;

    @Mock
    MachineShiftCalendarRepository machineShiftCalendarRepository;

    private ShiftGenerationService shiftGenerationService;

    @BeforeEach
    void setUp() {
        shiftGenerationService =
                new ShiftGenerationService(
                        properties,
                        shiftRepository,
                        machineShiftRepository,
                        machineShiftCalendarRepository,
                        new ShiftWindowCalculator());
    }

    @Test
    void generate_upsertsMatchingShiftsAndDeletesStale() {
        LocalDate today = LocalDate.now(ZONE);
        when(properties.factoryZoneId()).thenReturn(ZONE.getId());
        when(properties.horizonDays()).thenReturn(3);

        ShiftTypeSummary todayShift =
                new ShiftTypeSummary(
                        1L, today.getDayOfWeek().getValue(), LocalTime.of(8, 0), LocalTime.of(16, 0));
        when(shiftRepository.findAll()).thenReturn(List.of(todayShift));
        when(machineShiftRepository.findAllLinks())
                .thenReturn(List.of(new MachineShiftTypeLink(10L, 1L)));

        MachineShiftKey expectedKey = new MachineShiftKey(10L, 1L, today);
        when(machineShiftCalendarRepository.deleteStaleExpected(eq(today), eq(today.plusDays(2)), any()))
                .thenReturn(1);

        ShiftGenerationResult result = shiftGenerationService.generate();

        ArgumentCaptor<MachineShiftKey> keyCaptor = ArgumentCaptor.forClass(MachineShiftKey.class);
        verify(machineShiftCalendarRepository).upsertExpected(keyCaptor.capture(), any(), any());
        assertThat(keyCaptor.getValue()).isEqualTo(expectedKey);
        verify(machineShiftCalendarRepository)
                .deleteStaleExpected(eq(today), eq(today.plusDays(2)), eq(Set.of(expectedKey)));
        assertThat(result).isEqualTo(new ShiftGenerationResult(1, 1));
    }

    @Test
    void generate_skipsShiftWhenDayOfWeekDoesNotMatch() {
        LocalDate today = LocalDate.now(ZONE);
        int otherDay = today.getDayOfWeek().getValue() == 7 ? 1 : today.getDayOfWeek().getValue() + 1;

        when(properties.factoryZoneId()).thenReturn(ZONE.getId());
        when(properties.horizonDays()).thenReturn(1);

        ShiftTypeSummary otherDayShift =
                new ShiftTypeSummary(1L, otherDay, LocalTime.of(8, 0), LocalTime.of(16, 0));
        when(shiftRepository.findAll()).thenReturn(List.of(otherDayShift));
        when(machineShiftRepository.findAllLinks())
                .thenReturn(List.of(new MachineShiftTypeLink(10L, 1L)));
        when(machineShiftCalendarRepository.deleteStaleExpected(eq(today), eq(today), eq(Set.of())))
                .thenReturn(0);

        ShiftGenerationResult result = shiftGenerationService.generate();

        verify(machineShiftCalendarRepository, never()).upsertExpected(any(), any(), any());
        assertThat(result).isEqualTo(new ShiftGenerationResult(0, 0));
    }
}
