package one.nexus.p1.api;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.servlet.http.HttpServletRequest;
import one.nexus.p1.api.ApiModels.ErrorBody;
import one.nexus.p1.domain.DomainException;
import one.nexus.p1.domain.ErrorCode;

/**
 * P1-specific advice. Named distinctly from
 * {@code one.nexus.bootstrap.web.ApiExceptionHandler} so both can live on the
 * {@code one.nexus} component scan without a bean-name clash.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class P1ApiExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorBody> domain(DomainException ex, HttpServletRequest request) {
        HttpStatus status = switch (ex.errorCode()) {
            case TENANT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case TENANT_CODE_CONFLICT, TENANT_PARENT_CYCLE -> HttpStatus.CONFLICT;
            case CONTEXT_PLATFORM_REQUIRED, CONTEXT_TENANT_MISMATCH, DUTY_DENIED -> HttpStatus.FORBIDDEN;
            case QUOTA_EXCEEDED -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(body(ex.errorCode().code(), ex.getMessage(), request, ex.details()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .orElse("validation failed");
        return ResponseEntity.badRequest()
                .body(body(ErrorCode.TENANT_INVALID_CODE.code(), message, request, Map.of()));
    }

    private static ErrorBody body(String code, String message, HttpServletRequest request, Map<String, Object> details) {
        String trace = (String) request.getAttribute("trace_id");
        if (trace == null) {
            trace = MDC.get("trace_id");
        }
        return new ErrorBody(code, message, trace, details);
    }
}
