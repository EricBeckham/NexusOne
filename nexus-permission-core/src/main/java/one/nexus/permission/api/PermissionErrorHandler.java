package one.nexus.permission.api;

import java.util.Map;
import one.nexus.common.trace.TraceIds;
import one.nexus.p2.support.DomainException;
import one.nexus.permission.support.PermissionException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {PermissionController.class, PositionRoleController.class, EntryAccessController.class,
        MemberAccessController.class})
public class PermissionErrorHandler {
    @ExceptionHandler(PermissionException.class)
    public ResponseEntity<Map<String, Object>> handle(PermissionException ex) {
        return ResponseEntity.status(ex.httpStatus()).body(Map.of(
                "code", ex.errorCode().code(),
                "message", ex.getMessage(),
                "trace_id", TraceIds.currentOrUnknown(),
                "details", ex.details()));
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<Map<String, Object>> handleDomain(DomainException ex) {
        return ResponseEntity.status(ex.httpStatus()).body(Map.of(
                "code", ex.code(),
                "message", ex.getMessage(),
                "trace_id", TraceIds.currentOrUnknown(),
                "details", Map.of()));
    }
}
