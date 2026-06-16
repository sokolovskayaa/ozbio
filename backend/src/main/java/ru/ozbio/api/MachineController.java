package ru.ozbio.api;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import ru.ozbio.api.dto.CreateMachineRequest;
import ru.ozbio.api.dto.CreateMachineTypeRequest;
import ru.ozbio.api.dto.MachineResponse;
import ru.ozbio.api.dto.MachineTypeResponse;
import ru.ozbio.service.MachineService;

@RestController
@RequestMapping(path = "/machines", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Machines", description = "Станки")
public class MachineController {

    private final MachineService machineService;

    public MachineController(MachineService machineService) {
        this.machineService = machineService;
    }

    @PostMapping(path = "/types", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создать тип станка")
    MachineTypeResponse createType(@Valid @RequestBody CreateMachineTypeRequest request) {
        return machineService.createType(request);
    }

    @GetMapping("/types")
    @Operation(summary = "Список типов станков")
    List<MachineTypeResponse> listTypes() {
        return machineService.listTypes();
    }

    @DeleteMapping("/types/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить тип станка")
    void deleteType(@PathVariable long id) {
        machineService.deleteType(id);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создать станок")
    MachineResponse create(@Valid @RequestBody CreateMachineRequest request) {
        return machineService.create(request);
    }

    @GetMapping
    @Operation(summary = "Список станков")
    List<MachineResponse> list() {
        return machineService.list();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить станок")
    void delete(@PathVariable long id) {
        machineService.delete(id);
    }
}
