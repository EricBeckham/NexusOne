package one.nexus.p3.flow.api;

import java.util.Map;
import one.nexus.p3.flow.FlowException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = {"one.nexus.p3.flow", "one.nexus.p3.path"})
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnProperty(name = "nexus.flow.engine")
public class FlowExceptionHandler {
    @ExceptionHandler(FlowException.class)
    public ResponseEntity<Map<String, Object>> handle(FlowException ex) {
        return ResponseEntity.status(ex.httpStatus()).body(Map.of(
                "code", ex.errorCode().code(),
                "message", ex.getMessage(),
                "trace_id", one.nexus.common.trace.TraceIds.currentOrUnknown(),
                "details", ex.details()));
    }
}
