package ru.ozbio.api;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.ozbio.api.dto.CreateShiftTypeRequest;
import ru.ozbio.api.dto.LinkMachinesToShiftRequest;
import ru.ozbio.api.dto.ShiftTypeResponse;
import ru.ozbio.service.MachineShiftService;
import ru.ozbio.service.ShiftService;

@Order(4)
@RestController
@RequestMapping(path = "/shifts", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Shifts", description = "Смены")
public class ShiftController {

    private final ShiftService shiftService;
    private final MachineShiftService machineShiftService;

    public ShiftController(ShiftService shiftService, MachineShiftService machineShiftService) {
        this.shiftService = shiftService;
        this.machineShiftService = machineShiftService;
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

    @GetMapping("/{id}/machines")
    @Operation(summary = "Список станков, привязанных к смене")
    List<Long> listMachines(@PathVariable long id) {
        return machineShiftService.listMachineIds(id);
    }

    @PostMapping(path = "/{id}/machines", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Привязать станки к смене")
    void linkMachines(@PathVariable long id, @Valid @RequestBody LinkMachinesToShiftRequest request) {
        machineShiftService.linkMachines(id, request);
    }

    @DeleteMapping("/{id}/machines/{machineId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Отвязать станок от смены")
    void unlinkMachine(@PathVariable long id, @PathVariable long machineId) {
        machineShiftService.unlinkMachine(id, machineId);
    }
}
