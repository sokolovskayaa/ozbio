package ru.ozbio.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.ozbio.api.dto.CloseMachineShiftRequest;
import ru.ozbio.api.dto.MachineShiftClosableResponse;
import ru.ozbio.service.MachineShiftCloseService;
import ru.ozbio.service.MachineShiftQueryService;

@Order(5)
@RestController
@RequestMapping(path = "/machine-shifts", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Machine shifts", description = "Экземпляры смен на станках")
public class MachineShiftController {

    private final MachineShiftQueryService machineShiftQueryService;
    private final MachineShiftCloseService machineShiftCloseService;

    public MachineShiftController(
            MachineShiftQueryService machineShiftQueryService,
            MachineShiftCloseService machineShiftCloseService) {
        this.machineShiftQueryService = machineShiftQueryService;
        this.machineShiftCloseService = machineShiftCloseService;
    }

    @GetMapping("/closable")
    @Operation(summary = "Смены, которые уже можно закрыть")
    MachineShiftClosableResponse listClosable() {
        return machineShiftQueryService.findClosable();
    }

    @PostMapping(path = "/{id}/close", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Закрыть смену")
    void close(@PathVariable long id, @Valid @RequestBody CloseMachineShiftRequest request) {
        machineShiftCloseService.close(id, request);
    }
}
