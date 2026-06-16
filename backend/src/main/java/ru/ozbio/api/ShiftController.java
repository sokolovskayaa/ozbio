package ru.ozbio.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.ozbio.api.dto.CreateShiftTypeRequest;
import ru.ozbio.api.dto.ShiftTypeResponse;
import ru.ozbio.service.ShiftService;

@Order(4)
@RestController
@RequestMapping(path = "/shifts", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Shifts", description = "Смены")
public class ShiftController {

    private final ShiftService shiftService;

    public ShiftController(ShiftService shiftService) {
        this.shiftService = shiftService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Добавить тип смены")
    ShiftTypeResponse create(@Valid @RequestBody CreateShiftTypeRequest request) {
        return shiftService.create(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить тип смены")
    void delete(@PathVariable long id) {
        shiftService.delete(id);
    }
}
