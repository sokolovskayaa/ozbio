package ru.ozbio.api.handler;

import java.sql.SQLException;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.ozbio.engine.exception.NoMachineAvailableException;
import ru.ozbio.service.exception.InvalidReferenceException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        if (isForeignKeyViolation(ex)) {
            return problemDetail(
                    HttpStatus.CONFLICT,
                    "Resource in use",
                    "Cannot delete or update: resource is referenced by other records");
        }
        return problemDetail(
                HttpStatus.BAD_REQUEST,
                "Data integrity violation",
                ex.getMostSpecificCause().getMessage());
    }

    @ExceptionHandler(NoMachineAvailableException.class)
    ProblemDetail handleNoMachineAvailable(NoMachineAvailableException ex) {
        ProblemDetail detail =
                problemDetail(HttpStatus.CONFLICT, "No machine available", ex.getMessage());
        detail.setProperty("machineTypeId", ex.machineTypeId());
        detail.setProperty("operationId", ex.operationId());
        return detail;
    }

    @ExceptionHandler(InvalidReferenceException.class)
    ProblemDetail handleInvalidReference(InvalidReferenceException ex) {
        ProblemDetail detail =
                problemDetail(HttpStatus.BAD_REQUEST, "Invalid reference", ex.getMessage());
        detail.setProperty("field", ex.field());
        detail.setProperty("id", ex.id());
        return detail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return problemDetail(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail detail = problemDetail(HttpStatus.BAD_REQUEST, "Validation failed", null);
        detail.setProperty(
                "errors",
                ex.getBindingResult().getFieldErrors().stream()
                        .map(this::formatFieldError)
                        .toList());
        return detail;
    }

    private static ProblemDetail problemDetail(HttpStatus status, String title, String detailMessage) {
        ProblemDetail detail = ProblemDetail.forStatus(status);
        detail.setTitle(title);
        if (detailMessage != null) {
            detail.setDetail(detailMessage);
        }
        return detail;
    }

    private static boolean isForeignKeyViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof SQLException sqlException && "23503".equals(sqlException.getSQLState())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private Map<String, String> formatFieldError(FieldError error) {
        return Map.of("field", error.getField(), "message", error.getDefaultMessage());
    }
}
