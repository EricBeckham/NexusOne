package one.nexus.isolation.audit;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;

/**
 * Process-wide auditor installed by {@code IsolationKernel.create}. Static
 * file / cache / export / async guards call this so reject-before-execute
 * always emits an {@code IsolationAuditEvent}. Not a second tenant context.
 */
public final class IsolationAuditorHolder {
    private static final AtomicReference<IsolationAuditor> INSTALLED = new AtomicReference<>();

    private IsolationAuditorHolder() {}

    public static void install(IsolationAuditor auditor) {
        INSTALLED.set(Objects.requireNonNull(auditor, "auditor"));
    }

    public static IsolationAuditor currentOrNull() {
        return INSTALLED.get();
    }

    public static void exportAccepted(AccessContext context, String action, String resourceTenantId, String resourceId) {
        IsolationAuditor auditor = INSTALLED.get();
        if (auditor != null && context != null) {
            auditor.exportAccepted(context, action, resourceTenantId, resourceId);
        }
    }

    public static IsolationException reject(IsolationErrorCode code, AccessContext context, String detail) {
        IsolationAuditor auditor = INSTALLED.get();
        if (auditor != null) {
            return auditor.reject(code, context, detail);
        }
        String trace = context == null ? "missing-trace" : context.traceId();
        return new IsolationException(code, trace);
    }

    public static IsolationException reject(IsolationErrorCode code, String traceId, String actorId, String detail) {
        IsolationAuditor auditor = INSTALLED.get();
        if (auditor != null) {
            return auditor.reject(code, traceId, actorId, detail);
        }
        return new IsolationException(code, traceId);
    }
}
