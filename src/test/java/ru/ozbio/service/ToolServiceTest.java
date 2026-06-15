package ru.ozbio.service;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.ozbio.api.dto.CreateToolRequest;
import ru.ozbio.api.dto.ToolDetailRequest;
import ru.ozbio.persistence.ToolRepository;
import ru.ozbio.service.exception.InvalidReferenceException;
import ru.ozbio.service.exception.ToolInUseException;
import ru.ozbio.service.model.CreateToolCommand;
import ru.ozbio.service.model.ToolDetailLine;
import ru.ozbio.service.model.ToolSummary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolServiceTest {

    @Mock
    ToolRepository toolRepository;

    @InjectMocks
    ToolService toolService;

    @Test
    void create_persistsToolWithDetails() {
        when(toolRepository.detailExists(1L)).thenReturn(true);
        when(toolRepository.insert(any(CreateToolCommand.class)))
                .thenReturn(new ToolSummary(10L, "Drill", Duration.ofMinutes(45)));
        when(toolRepository.findDetailsByToolId(10L))
                .thenReturn(List.of(new ToolDetailLine(1L, "Body", 2)));

        var response =
                toolService.create(
                        new CreateToolRequest(
                                "Drill",
                                Duration.ofMinutes(45),
                                List.of(new ToolDetailRequest(1L, 2))));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.details()).hasSize(1);
        verify(toolRepository).insert(any(CreateToolCommand.class));
    }

    @Test
    void create_rejectsUnknownDetail() {
        when(toolRepository.detailExists(99L)).thenReturn(false);

        assertThatThrownBy(
                        () ->
                                toolService.create(
                                        new CreateToolRequest(
                                                "Drill",
                                                Duration.ofMinutes(45),
                                                List.of(new ToolDetailRequest(99L, 1)))))
                .isInstanceOf(InvalidReferenceException.class);

        verify(toolRepository, never()).insert(any());
    }

    @Test
    void delete_removesToolAndSpecification() {
        when(toolRepository.existsById(10L)).thenReturn(true);
        when(toolRepository.isReferenced(10L)).thenReturn(false);
        when(toolRepository.deleteById(10L)).thenReturn(true);

        toolService.delete(10L);

        verify(toolRepository).deleteById(10L);
    }

    @Test
    void delete_rejectsReferencedTool() {
        when(toolRepository.existsById(10L)).thenReturn(true);
        when(toolRepository.isReferenced(10L)).thenReturn(true);

        assertThatThrownBy(() -> toolService.delete(10L)).isInstanceOf(ToolInUseException.class);

        verify(toolRepository, never()).deleteById(10L);
    }
}
