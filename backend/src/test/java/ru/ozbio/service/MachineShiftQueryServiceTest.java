package ru.ozbio.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.ozbio.persistence.MachineShiftInstanceRepository;
import ru.ozbio.service.model.MachineShiftClosableSummary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MachineShiftQueryServiceTest {

    @Mock
    MachineShiftInstanceRepository machineShiftInstanceRepository;

    @InjectMocks
    MachineShiftQueryService machineShiftQueryService;

    @Test
    void findClosable_mapsRepositoryRows() {
        when(machineShiftInstanceRepository.findClosable(any()))
                .thenReturn(
                        List.of(
                                new MachineShiftClosableSummary(
                                        1L,
                                        10L,
                                        2L,
                                        LocalDate.of(2026, 5, 22),
                                        Instant.parse("2026-05-22T05:00:00Z"),
                                        Instant.parse("2026-05-22T13:00:00Z"),
                                        "EXPECTED",
                                        5,
                                        LocalTime.of(8, 0),
                                        LocalTime.of(16, 0))));

        var response = machineShiftQueryService.findClosable();

        verify(machineShiftInstanceRepository).findClosable(any(Instant.class));
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().id()).isEqualTo(1L);
        assertThat(response.items().getFirst().machineId()).isEqualTo(10L);
        assertThat(response.items().getFirst().shiftType().dayOfWeek()).isEqualTo(5);
    }
}
