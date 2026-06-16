package ru.ozbio.api.dto;

import java.time.LocalTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import ru.ozbio.service.model.CreateShiftTypeCommand;

public record CreateShiftTypeRequest(
        @Min(1) @Max(7) int dayOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime) {

    public CreateShiftTypeRequest {
        if (startTime != null && endTime != null && startTime.equals(endTime)) {
            throw new IllegalArgumentException("startTime and endTime must differ");
        }
    }

    public CreateShiftTypeCommand toCommand() {
        return new CreateShiftTypeCommand(dayOfWeek, startTime, endTime);
    }
}
