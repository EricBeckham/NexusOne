package one.nexus.permission.support;

import java.util.Map;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;

public final class PermissionException extends NexusException {
    public static final ErrorCode TENANT_BOUNDARY = ErrorCode.of("P2", "PERM", 1);
    public static final ErrorCode EXPLICIT_DENY = ErrorCode.of("P2", "PERM", 2);
    public static final ErrorCode NO_GRANT = ErrorCode.of("P2", "PERM", 3);
    public static final ErrorCode EMPTY_SCOPE = ErrorCode.of("P2", "PERM", 4);
    public static final ErrorCode SNAPSHOT_UNAVAILABLE = ErrorCode.of("P2", "PERM", 5);
    public static final ErrorCode SNAPSHOT_STALE = ErrorCode.of("P2", "PERM", 6);
    public static final ErrorCode VERSION_REGRESSED = ErrorCode.of("P2", "PERM", 7);
    public static final ErrorCode REJECTED_IDENT = ErrorCode.of("P2", "PERM", 8);
    public static final ErrorCode MISSING_CONTEXT = ErrorCode.of("P2", "PERM", 9);
    public static final ErrorCode TENANT_PREDICATE = ErrorCode.of("P2", "PERM", 10);
    public static final ErrorCode NOT_FOUND = ErrorCode.of("P2", "PERM", 11);
    public static final ErrorCode CONFLICT = ErrorCode.of("P2", "PERM", 12);
    public static final ErrorCode CLIENT_TENANT = ErrorCode.of("P2", "PERM", 13);
    public static final ErrorCode DUTY_CONFLICT = ErrorCode.of("P2", "PERM", 14);

    public PermissionException(ErrorCode errorCode, int httpStatus, String message) {
        super(errorCode, httpStatus, message);
    }

    public PermissionException(ErrorCode errorCode, int httpStatus, String message, Map<String, Object> details) {
        super(errorCode, httpStatus, message, details);
    }

    public static PermissionException denied(String reason) {
        ErrorCode code = switch (reason) {
            case "tenant_boundary" -> TENANT_BOUNDARY;
            case "explicit_deny" -> EXPLICIT_DENY;
            case "no_grant" -> NO_GRANT;
            case "empty_scope" -> EMPTY_SCOPE;
            case "duty_conflict" -> DUTY_CONFLICT;
            default -> NO_GRANT;
        };
        return new PermissionException(code, 403, reason);
    }

    public static PermissionException snapshotUnavailable(String message) {
        return new PermissionException(SNAPSHOT_UNAVAILABLE, 503, message);
    }

    public static PermissionException tenantPredicateRequired() {
        return tenantPredicateRequired("DataScope must not strip tenant_id");
    }

    public static PermissionException tenantPredicateRequired(String message) {
        return new PermissionException(TENANT_PREDICATE, 403, message);
    }

    public static PermissionException missingContext() {
        return new PermissionException(MISSING_CONTEXT, 403, "TenantContext is required");
    }

    public static PermissionException notFound(String message) {
        return new PermissionException(NOT_FOUND, 404, message);
    }

    public static PermissionException conflict(String message) {
        return new PermissionException(CONFLICT, 409, message);
    }

    public static PermissionException dutyConflict(String message) {
        return new PermissionException(DUTY_CONFLICT, 409, message);
    }

    public static PermissionException badRequest(String unusedCode, String message) {
        return new PermissionException(REJECTED_IDENT, 400, message);
    }

    public static PermissionException clientTenantRejected() {
        return new PermissionException(CLIENT_TENANT, 400,
                "client-supplied tenant_id is rejected; use a server-issued context");
    }
}
