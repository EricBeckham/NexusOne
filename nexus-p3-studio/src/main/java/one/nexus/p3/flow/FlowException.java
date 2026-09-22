package one.nexus.p3.flow;

import java.util.Map;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;

public final class FlowException extends NexusException {
    public static final ErrorCode MISSING_CONTEXT = ErrorCode.of("P3", "FLOW", 1);
    public static final ErrorCode NOT_FOUND = ErrorCode.of("P3", "FLOW", 2);
    public static final ErrorCode CONFLICT = ErrorCode.of("P3", "FLOW", 3);
    public static final ErrorCode FORBIDDEN = ErrorCode.of("P3", "FLOW", 4);
    public static final ErrorCode INVALID_ASSIGNEE = ErrorCode.of("P3", "FLOW", 5);
    public static final ErrorCode CLIENT_TENANT = ErrorCode.of("P3", "FLOW", 6);
    public static final ErrorCode NOT_RUNNING = ErrorCode.of("P3", "FLOW", 7);
    public static final ErrorCode BAD_REQUEST = ErrorCode.of("P3", "FLOW", 8);

    public FlowException(ErrorCode errorCode, int httpStatus, String message) {
        super(errorCode, httpStatus, message);
    }

    public FlowException(ErrorCode errorCode, int httpStatus, String message, Map<String, Object> details) {
        super(errorCode, httpStatus, message, details);
    }

    public static FlowException missingContext() {
        return new FlowException(MISSING_CONTEXT, 403, "TenantContext is required");
    }

    public static FlowException notFound(String message) {
        return new FlowException(NOT_FOUND, 404, message);
    }

    public static FlowException conflict(String message) {
        return new FlowException(CONFLICT, 409, message);
    }

    public static FlowException forbidden(String message) {
        return new FlowException(FORBIDDEN, 403, message);
    }

    public static FlowException invalidAssignee(String message) {
        return new FlowException(INVALID_ASSIGNEE, 400, message);
    }

    public static FlowException clientTenantRejected() {
        return new FlowException(CLIENT_TENANT, 400,
                "client-supplied tenant_id is rejected; use a server-issued context");
    }

    public static FlowException notRunning(String message) {
        return new FlowException(NOT_RUNNING, 409, message);
    }

    public static FlowException badRequest(String message) {
        return new FlowException(BAD_REQUEST, 400, message);
    }
}
