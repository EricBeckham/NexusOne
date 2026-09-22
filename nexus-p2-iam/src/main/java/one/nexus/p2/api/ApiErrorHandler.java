package one.nexus.p2.api;

import one.nexus.p2.support.DomainException;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiErrorHandler {
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<Map<String, Object>> handle(DomainException ex) {
        String traceId = MDC.get("trace_id");
        return ResponseEntity.status(ex.httpStatus()).body(Map.of(
                "code", ex.code(),
                "message", ex.getMessage(),
                "trace_id", traceId == null ? "" : traceId,
                "details", Map.of()));
    }
}
