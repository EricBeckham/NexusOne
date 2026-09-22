package one.nexus.p3.path.api;

import java.util.Map;
import one.nexus.common.error.ApiError;
import one.nexus.common.error.NexusException;
import one.nexus.common.trace.TraceIds;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "one.nexus.p3.path")
@Order(Ordered.HIGHEST_PRECEDENCE + 8)
public class PathExceptionHandler {
    @ExceptionHandler(NexusException.class)
    public ResponseEntity<Map<String, Object>> nexus(NexusException ex) {
        return ResponseEntity.status(HttpStatus.valueOf(ex.httpStatus()))
                .body(new ApiError(ex.errorCode().code(), ex.getMessage(), TraceIds.currentOrUnknown(), ex.details())
                        .toJsonMap());
    }
}
