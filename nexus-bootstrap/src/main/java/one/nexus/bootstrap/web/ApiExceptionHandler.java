package one.nexus.bootstrap.web;

import java.util.Map;
import one.nexus.common.error.ApiError;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.common.trace.TraceIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NexusException.class)
    public ResponseEntity<Map<String, Object>> handleNexus(NexusException ex) {
        return body(ex.httpStatus(), ex.errorCode(), ex.getMessage(), ex.details());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleMissing(NoResourceFoundException ex) {
        return body(404, ErrorCode.API_NOT_FOUND, "Resource not found", Map.of());
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
