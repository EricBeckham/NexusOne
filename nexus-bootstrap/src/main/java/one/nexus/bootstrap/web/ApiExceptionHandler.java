package one.nexus.bootstrap.web;

import java.util.Map;
import one.nexus.common.error.ApiError;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.common.trace.TraceIds;
import one.nexus.isolation.api.IsolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NexusException.class)
    public ResponseEntity<Map<String, Object>> handleNexus(NexusException ex) {
        return body(ex.httpStatus(), ex.errorCode(), ex.getMessage(), ex.details());
    }

    @ExceptionHandler(IsolationException.class)
    public ResponseEntity<Map<String, Object>> handleIsolation(IsolationException ex) {
        ApiError error = new ApiError(ex.code(), ex.getMessage(), ex.traceId(), Map.of());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error.toJsonMap());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleMissing(NoResourceFoundException ex) {
        return body(404, ErrorCode.API_NOT_FOUND, "Resource not found", Map.of());
    }

    /**
     * Services throw {@link ResponseStatusException} for deliberate 4xx outcomes (e.g. catalog SKU
     * without a product definition). Keep that status and reason instead of the 500 catch-all below.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException ex) {
        int status = ex.getStatusCode().value();
        if (status >= 500) log.error("response status error", ex);
        String reason = ex.getReason();
        String message = reason == null || reason.isBlank()
                ? HttpStatus.valueOf(status).getReasonPhrase()
                : reason;
        return body(status, codeFor(status), message, Map.of());
    }

    private static ErrorCode codeFor(int status) {
        return switch (status) {
            case 401, 403 -> ErrorCode.API_FORBIDDEN;
            case 404 -> ErrorCode.API_NOT_FOUND;
            case 409 -> ErrorCode.API_CONFLICT;
            case 422 -> ErrorCode.API_UNPROCESSABLE;
            default -> status >= 500 ? ErrorCode.API_INTERNAL : ErrorCode.API_BAD_REQUEST;
        };
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnknown(Exception ex) {
        log.error("unhandled error", ex);
        return body(500, ErrorCode.API_INTERNAL, "Internal error", Map.of());
    }

    private static ResponseEntity<Map<String, Object>> body(
            int status, ErrorCode code, String message, Map<String, Object> details) {
        ApiError error = ApiError.of(code, message, TraceIds.currentOrUnknown(), details);
        return ResponseEntity.status(HttpStatus.valueOf(status)).body(error.toJsonMap());
    }
}
