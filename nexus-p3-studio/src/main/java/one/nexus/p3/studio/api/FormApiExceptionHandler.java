package one.nexus.p3.studio.api;

import java.util.Map;
import one.nexus.common.error.ApiError;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.common.trace.TraceIds;
import one.nexus.isolation.api.IsolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "one.nexus.p3.studio")
@Order(Ordered.HIGHEST_PRECEDENCE + 7)
public class FormApiExceptionHandler {
    @ExceptionHandler(NexusException.class)
    public ResponseEntity<Map<String, Object>> nexus(NexusException ex) {
        return body(ex.httpStatus(), ex.errorCode().code(), ex.getMessage(), ex.details());
    }

    @ExceptionHandler(IsolationException.class)
    public ResponseEntity<Map<String, Object>> isolation(IsolationException ex) {
        return body(403, ex.code(), ex.getMessage(), Map.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> bad(IllegalArgumentException ex) {
        return body(400, ErrorCode.FORM_INVALID.code(), ex.getMessage(), Map.of());
    }

    private static ResponseEntity<Map<String, Object>> body(
            int status, String code, String message, Map<String, Object> details) {
        return ResponseEntity.status(HttpStatus.valueOf(status))
                .body(new ApiError(code, message, TraceIds.currentOrUnknown(), details).toJsonMap());
    }
}
