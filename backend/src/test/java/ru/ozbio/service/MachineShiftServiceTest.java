package ru.ozbio.service;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.ozbio.api.dto.LinkMachinesToShiftRequest;
import ru.ozbio.persistence.MachineShiftRepository;
import ru.ozbio.persistence.ShiftRepository;
import ru.ozbio.service.exception.InvalidReferenceException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MachineShiftServiceTest {

    @Mock
    MachineShiftRepository machineShiftRepository;

    @Mock
    ShiftRepository shiftRepository;

    @InjectMocks
    MachineShiftService machineShiftService;

    @Test
    void linkMachines_validatesShiftAndMachines() {
        when(shiftRepository.existsById(1L)).thenReturn(true);
        when(machineShiftRepository.findExistingMachineIds(List.of(10L, 11L)))
                .thenReturn(java.util.Set.of(10L, 11L));

        machineShiftService.linkMachines(1L, new LinkMachinesToShiftRequest(List.of(10L, 11L)));

        verify(machineShiftRepository).linkMachines(1L, List.of(10L, 11L));
    }

    @Test
    void linkMachines_rejectsUnknownMachine() {
        when(shiftRepository.existsById(1L)).thenReturn(true);
        when(machineShiftRepository.findExistingMachineIds(List.of(99L))).thenReturn(java.util.Set.of());

        assertThatThrownBy(
                        () ->
                                machineShiftService.linkMachines(
                                        1L, new LinkMachinesToShiftRequest(List.of(99L))))
                .isInstanceOf(InvalidReferenceException.class);

        verify(machineShiftRepository, never()).linkMachines(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }
}
