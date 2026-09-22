package one.nexus.p2.api;

import one.nexus.common.trace.TraceIds;
import one.nexus.p2.support.DomainException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = {AuthController.class, OrganizationController.class})
public class ApiErrorHandler {
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<Map<String, Object>> handle(DomainException ex) {
        return ResponseEntity.status(ex.httpStatus()).body(Map.of(
                "code", ex.code(),
                "message", ex.getMessage(),
                "trace_id", TraceIds.currentOrUnknown(),
                "details", Map.of()));
    }
}
