package ru.ozbio.service;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.ozbio.api.dto.CreateDetailRequest;
import ru.ozbio.api.dto.OperationRequest;
import ru.ozbio.persistence.DetailRepository;
import ru.ozbio.service.exception.InvalidReferenceException;
import ru.ozbio.service.model.CreateDetailCommand;
import ru.ozbio.service.model.DetailSummary;
import ru.ozbio.service.model.OperationLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DetailServiceTest {

    @Mock
    DetailRepository detailRepository;

    @InjectMocks
    DetailService detailService;

    @Test
    void create_persistsDetailWithOperations() {
        when(detailRepository.machineTypeExists(1L)).thenReturn(true);
        when(detailRepository.insert(any(CreateDetailCommand.class)))
                .thenReturn(new DetailSummary(10L, "Valve body"));
        when(detailRepository.findOperationsByDetailId(10L))
                .thenReturn(
                        List.of(
                                new OperationLine(
                                        100L, 1, Duration.ofMinutes(30), Duration.ZERO, 1L)));

        var response =
                detailService.create(
                        new CreateDetailRequest(
                                "Valve body",
                                List.of(
                                        new OperationRequest(30, 1L, 0))));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Valve body");
        assertThat(response.operations()).hasSize(1);
        verify(detailRepository).insert(any(CreateDetailCommand.class));
    }

    @Test
    void create_assignsOperationStepsFromRequestOrder() {
        when(detailRepository.machineTypeExists(1L)).thenReturn(true);
        when(detailRepository.insert(any(CreateDetailCommand.class)))
                .thenReturn(new DetailSummary(10L, "Valve body"));
        when(detailRepository.findOperationsByDetailId(10L)).thenReturn(List.of());

        var request =
                new CreateDetailRequest(
                        "Valve body",
                        List.of(
                                new OperationRequest(30, 1L, 0),
                                new OperationRequest(20, 1L, 0)));
        detailService.create(request);

        ArgumentCaptor<CreateDetailCommand> captor = ArgumentCaptor.forClass(CreateDetailCommand.class);
        verify(detailRepository).insert(captor.capture());
        assertThat(captor.getValue().operations())
                .extracting(CreateDetailCommand.Operation::step)
                .containsExactly(1, 2);
        assertThat(request.toCommand().operations())
                .extracting(CreateDetailCommand.Operation::step)
                .containsExactly(1, 2);
    }

    @Test
    void create_rejectsUnknownMachineType() {
        when(detailRepository.machineTypeExists(99L)).thenReturn(false);

        assertThatThrownBy(
                        () ->
                                detailService.create(
                                        new CreateDetailRequest(
                                                "Valve body",
                                                List.of(
                                                        new OperationRequest(30, 99L, 0)))))
                .isInstanceOf(InvalidReferenceException.class);

        verify(detailRepository, never()).insert(any());
    }

    @Test
    void delete_callsRepository() {
        detailService.delete(10L);

        verify(detailRepository).deleteById(10L);
    }
}
