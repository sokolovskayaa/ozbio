package ru.ozbio.api.dto;

import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import ru.ozbio.service.model.CreateToolCommand;

public record CreateToolRequest(
        @NotBlank String name,
        @Min(0) int assembleDuration,
        @NotEmpty @Valid List<ToolDetailRequest> details) {

    public CreateToolCommand toCommand() {
        return new CreateToolCommand(
                name.trim(),
                Duration.ofMinutes(assembleDuration),
                details.stream()
                        .map(detail -> new CreateToolCommand.Detail(detail.detailId(), detail.count()))
                        .toList());
    }
}
