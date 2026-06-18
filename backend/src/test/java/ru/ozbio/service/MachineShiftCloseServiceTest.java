package ru.ozbio.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.ozbio.api.dto.CloseMachineShiftRequest;
import ru.ozbio.api.dto.ShiftCompletionRequest;
import ru.ozbio.persistence.DetailRepository;
import ru.ozbio.persistence.MachineRepository;
import ru.ozbio.persistence.MachineShiftInstanceRepository;
import ru.ozbio.persistence.ShiftCompletionRepository;
import ru.ozbio.service.exception.InvalidReferenceException;
import ru.ozbio.service.exception.OperationMachineTypeMismatchException;
import ru.ozbio.service.exception.ResourceNotFoundException;
import ru.ozbio.service.exception.ShiftAlreadyClosedException;
import ru.ozbio.service.model.MachineShiftCloseTarget;
import ru.ozbio.service.model.MachineSummary;
import ru.ozbio.service.model.OperationLine;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MachineShiftCloseServiceTest {

    @Mock
    MachineShiftInstanceRepository machineShiftInstanceRepository;

    @Mock
    MachineRepository machineRepository;

    @Mock
    DetailRepository detailRepository;

    @Mock
    ShiftCompletionRepository shiftCompletionRepository;

    @InjectMocks
    MachineShiftCloseService machineShiftCloseService;

    @Test
    void close_emptyCompletions_setsClosedEmpty() {
        when(machineShiftInstanceRepository.findByIdForUpdate(1L))
                .thenReturn(
                        Optional.of(
                                new MachineShiftCloseTarget(
                                        1L, 10L, Instant.parse("2026-05-22T05:00:00Z"), "EXPECTED")));

        machineShiftCloseService.close(1L, new CloseMachineShiftRequest(List.of()));

        verify(machineShiftInstanceRepository).updateClosed(eq(1L), eq("CLOSED_EMPTY"), any(Instant.class));
        verify(shiftCompletionRepository, never()).insertAll(any(Long.class), any());
    }

    @Test
    void close_withCompletions_insertsAndSetsClosed() {
        when(machineShiftInstanceRepository.findByIdForUpdate(1L))
                .thenReturn(
                        Optional.of(
                                new MachineShiftCloseTarget(
                                        1L, 10L, Instant.parse("2026-05-22T05:00:00Z"), "EXPECTED")));
        when(machineRepository.findMachineById(10L))
                .thenReturn(Optional.of(new MachineSummary(10L, 2L, "Lathe")));
        when(detailRepository.findOperationsByIds(List.of(100L)))
                .thenReturn(
                        Map.of(
                                100L,
                                new OperationLine(
                                        100L, 1, Duration.ofMinutes(10), Duration.ZERO, 2L)));

        var request =
                new CloseMachineShiftRequest(List.of(new ShiftCompletionRequest(100L, 12)));

        machineShiftCloseService.close(1L, request);

        verify(shiftCompletionRepository).insertAll(1L, request.completions());
        verify(machineShiftInstanceRepository).updateClosed(eq(1L), eq("CLOSED"), any(Instant.class));
    }

    @Test
    void close_rejectsUnknownShift() {
        when(machineShiftInstanceRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () -> machineShiftCloseService.close(99L, new CloseMachineShiftRequest(List.of())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void close_rejectsAlreadyClosed() {
        when(machineShiftInstanceRepository.findByIdForUpdate(1L))
                .thenReturn(
                        Optional.of(
                                new MachineShiftCloseTarget(
                                        1L, 10L, Instant.parse("2026-05-22T05:00:00Z"), "CLOSED")));

        assertThatThrownBy(
                        () -> machineShiftCloseService.close(1L, new CloseMachineShiftRequest(List.of())))
                .isInstanceOf(ShiftAlreadyClosedException.class);
    }

    @Test
    void close_rejectsShiftNotStarted() {
        when(machineShiftInstanceRepository.findByIdForUpdate(1L))
                .thenReturn(
                        Optional.of(
                                new MachineShiftCloseTarget(
                                        1L,
                                        10L,
                                        Instant.parse("2099-01-01T00:00:00Z"),
                                        "EXPECTED")));

        assertThatThrownBy(
                        () -> machineShiftCloseService.close(1L, new CloseMachineShiftRequest(List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not started");
    }

    @Test
    void close_rejectsDuplicateOperationId() {
        when(machineShiftInstanceRepository.findByIdForUpdate(1L))
                .thenReturn(
                        Optional.of(
                                new MachineShiftCloseTarget(
                                        1L, 10L, Instant.parse("2026-05-22T05:00:00Z"), "EXPECTED")));

        var request =
                new CloseMachineShiftRequest(
                        List.of(
                                new ShiftCompletionRequest(100L, 5),
                                new ShiftCompletionRequest(100L, 3)));

        assertThatThrownBy(() -> machineShiftCloseService.close(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate operationId");
    }

    @Test
    void close_rejectsUnknownOperation() {
        when(machineShiftInstanceRepository.findByIdForUpdate(1L))
                .thenReturn(
                        Optional.of(
                                new MachineShiftCloseTarget(
                                        1L, 10L, Instant.parse("2026-05-22T05:00:00Z"), "EXPECTED")));
        when(machineRepository.findMachineById(10L))
                .thenReturn(Optional.of(new MachineSummary(10L, 2L, "Lathe")));
        when(detailRepository.findOperationsByIds(List.of(100L))).thenReturn(Map.of());

        assertThatThrownBy(
                        () ->
                                machineShiftCloseService.close(
                                        1L,
                                        new CloseMachineShiftRequest(
                                                List.of(new ShiftCompletionRequest(100L, 1)))))
                .isInstanceOf(InvalidReferenceException.class);
    }

    @Test
    void close_rejectsMachineTypeMismatch() {
        when(machineShiftInstanceRepository.findByIdForUpdate(1L))
                .thenReturn(
                        Optional.of(
                                new MachineShiftCloseTarget(
                                        1L, 10L, Instant.parse("2026-05-22T05:00:00Z"), "EXPECTED")));
        when(machineRepository.findMachineById(10L))
                .thenReturn(Optional.of(new MachineSummary(10L, 2L, "Lathe")));
        when(detailRepository.findOperationsByIds(List.of(100L)))
                .thenReturn(
                        Map.of(
                                100L,
                                new OperationLine(
                                        100L, 1, Duration.ofMinutes(10), Duration.ZERO, 9L)));

        assertThatThrownBy(
                        () ->
                                machineShiftCloseService.close(
                                        1L,
                                        new CloseMachineShiftRequest(
                                                List.of(new ShiftCompletionRequest(100L, 1)))))
                .isInstanceOf(OperationMachineTypeMismatchException.class);

        verify(shiftCompletionRepository, never()).insertAll(any(Long.class), any());
        verify(machineShiftInstanceRepository, never()).updateClosed(any(Long.class), any(), any());
    }
}
