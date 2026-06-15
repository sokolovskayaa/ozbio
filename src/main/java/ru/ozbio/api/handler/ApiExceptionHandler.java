package ru.ozbio.api.handler;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.ozbio.service.exception.DetailInUseException;
import ru.ozbio.service.exception.DetailNotFoundException;
import ru.ozbio.service.exception.InvalidReferenceException;
import ru.ozbio.service.exception.MachineNotFoundException;
import ru.ozbio.service.exception.MachineTypeInUseException;
import ru.ozbio.service.exception.MachineTypeNotFoundException;
import ru.ozbio.service.exception.ToolInUseException;
import ru.ozbio.service.exception.ToolNotFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DetailNotFoundException.class)
    ProblemDetail handleDetailNotFound(DetailNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        detail.setTitle("Detail not found");
        detail.setDetail(ex.getMessage());
        detail.setProperty("detailId", ex.detailId());
        return detail;
    }

    @ExceptionHandler(DetailInUseException.class)
    ProblemDetail handleDetailInUse(DetailInUseException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        detail.setTitle("Detail in use");
        detail.setDetail(ex.getMessage());
        detail.setProperty("detailId", ex.detailId());
        return detail;
    }

    @ExceptionHandler(MachineTypeNotFoundException.class)
    ProblemDetail handleMachineTypeNotFound(MachineTypeNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        detail.setTitle("Machine type not found");
        detail.setDetail(ex.getMessage());
        detail.setProperty("machineTypeId", ex.machineTypeId());
        return detail;
    }

    @ExceptionHandler(MachineTypeInUseException.class)
    ProblemDetail handleMachineTypeInUse(MachineTypeInUseException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        detail.setTitle("Machine type in use");
        detail.setDetail(ex.getMessage());
        detail.setProperty("machineTypeId", ex.machineTypeId());
        return detail;
    }

    @ExceptionHandler(MachineNotFoundException.class)
    ProblemDetail handleMachineNotFound(MachineNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        detail.setTitle("Machine not found");
        detail.setDetail(ex.getMessage());
        detail.setProperty("machineId", ex.machineId());
        return detail;
    }

    @ExceptionHandler(ToolNotFoundException.class)
    ProblemDetail handleToolNotFound(ToolNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        detail.setTitle("Tool not found");
        detail.setDetail(ex.getMessage());
        detail.setProperty("toolId", ex.toolId());
        return detail;
    }

    @ExceptionHandler(ToolInUseException.class)
    ProblemDetail handleToolInUse(ToolInUseException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        detail.setTitle("Tool in use");
        detail.setDetail(ex.getMessage());
        detail.setProperty("toolId", ex.toolId());
        return detail;
    }

    @ExceptionHandler(InvalidReferenceException.class)
    ProblemDetail handleInvalidReference(InvalidReferenceException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setTitle("Invalid reference");
        detail.setDetail(ex.getMessage());
        detail.setProperty("field", ex.field());
        detail.setProperty("id", ex.id());
        return detail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setTitle("Invalid request");
        detail.setDetail(ex.getMessage());
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setTitle("Validation failed");
        detail.setProperty(
                "errors",
                ex.getBindingResult().getFieldErrors().stream()
                        .map(this::formatFieldError)
                        .toList());
        return detail;
    }

    private Map<String, String> formatFieldError(FieldError error) {
        return Map.of("field", error.getField(), "message", error.getDefaultMessage());
    }
}
