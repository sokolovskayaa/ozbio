package scheduler.api.http;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import scheduler.service.SchedulingException;

@RestControllerAdvice
public class SchedulingExceptionHandler {

    @ExceptionHandler(SchedulingException.class)
    public ResponseEntity<Map<String, String>> handleScheduling(SchedulingException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }
}
