package ru.ozbio.service;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.ozbio.api.dto.CreateShiftTypeRequest;
import ru.ozbio.persistence.ShiftRepository;
import ru.ozbio.service.model.CreateShiftTypeCommand;
import ru.ozbio.service.model.ShiftTypeSummary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShiftServiceTest {

    @Mock
    ShiftRepository shiftRepository;

    @InjectMocks
    ShiftService shiftService;

    @Test
    void create_persistsShiftType() {
        when(shiftRepository.insert(
                        new CreateShiftTypeCommand(1, LocalTime.of(8, 0), LocalTime.of(16, 0))))
                .thenReturn(new ShiftTypeSummary(1L, 1, LocalTime.of(8, 0), LocalTime.of(16, 0)));

        var response =
                shiftService.create(
                        new CreateShiftTypeRequest(1, LocalTime.of(8, 0), LocalTime.of(16, 0)));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.dayOfWeek()).isEqualTo(1);
        assertThat(response.startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(response.endTime()).isEqualTo(LocalTime.of(16, 0));
    }

    @Test
    void delete_callsRepository() {
        shiftService.delete(1L);

        verify(shiftRepository).deleteById(1L);
    }
}
