package one.nexus.p3.api;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.servlet.http.HttpServletRequest;
import one.nexus.isolation.api.IsolationException;
import one.nexus.p3.api.ApiModels.ErrorBody;
import one.nexus.p3.domain.P3ErrorCodes;
import one.nexus.p3.domain.P3Exception;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class P3ApiExceptionHandler {
    @ExceptionHandler(P3Exception.class)
    public ResponseEntity<ErrorBody> domain(P3Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.httpStatus())
                .body(body(ex.errorCode().code(), ex.getMessage(), request, ex.details()));
    }

    @ExceptionHandler(IsolationException.class)
    public ResponseEntity<ErrorBody> isolation(IsolationException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(body(ex.code() == null ? P3ErrorCodes.MISSING_TENANT.code() : ex.code(),
                        ex.getMessage(), request, Map.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .orElse("validation failed");
        return ResponseEntity.badRequest()
                .body(body(P3ErrorCodes.QUERY_INVALID.code(), message, request, Map.of()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorBody> unreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(body(P3ErrorCodes.QUERY_INVALID.code(), "malformed request body", request, Map.of()));
    }

    private static ErrorBody body(String code, String message, HttpServletRequest request, Map<String, Object> details) {
        String trace = (String) request.getAttribute("trace_id");
        if (trace == null) {
            trace = MDC.get("trace_id");
        }
        if (trace == null) {
            trace = "missing";
        }
        return new ErrorBody(code, message, trace, details);
    }
}
