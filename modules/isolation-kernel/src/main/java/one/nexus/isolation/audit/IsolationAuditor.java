package one.nexus.isolation.audit;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.IsolationAuditEvent;
import one.nexus.isolation.api.IsolationAuditSink;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.api.PlatformContext;

public final class IsolationAuditor {
    private final IsolationAuditSink sink;

    public IsolationAuditor(IsolationAuditSink sink) {
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    public void platformIssued(PlatformContext context) {
        sink.record(new IsolationAuditEvent(
                UUID.randomUUID(),
                Instant.now(),
                IsolationAuditEvent.PLATFORM_CONTEXT_ISSUED,
                null,
                context.actor().actorId(),
                null,
                context.path(),
                context.reason(),
                context.traceId(),
                Map.of("actorType", context.actor().actorType())
        ));
    }

    public void exportAccepted(AccessContext context, String action, String resourceTenantId, String resourceId) {
        Objects.requireNonNull(context, "context");
        String safeAction = action == null || action.isBlank() ? "submit" : action.trim();
        sink.record(new IsolationAuditEvent(
                UUID.randomUUID(),
                Instant.now(),
                IsolationAuditEvent.EXPORT_ACCEPTED,
                null,
                context.actor().actorId(),
                resourceTenantId,
                context instanceof PlatformContext platform ? platform.path() : null,
                "export " + safeAction,
                context.traceId(),
                Map.of(
                        "action",
                        safeAction,
                        "resourceId",
                        resourceId == null ? "" : resourceId)
        ));
    }

    public IsolationException reject(IsolationErrorCode code, AccessContext context, String detail) {
        String traceId = context == null ? "missing-trace" : context.traceId();
        String actorId = context == null ? null : context.actor().actorId();
        String tenantId = context == null ? null : context.boundTenantId().orElse(null);
        String path = context instanceof PlatformContext platform ? platform.path() : null;
        sink.record(new IsolationAuditEvent(
                UUID.randomUUID(),
                Instant.now(),
                IsolationAuditEvent.ISOLATION_REJECT,
                code,
                actorId,
                tenantId,
                path,
                code.defaultMessage(),
                traceId,
                detail == null ? Map.of() : Map.of("detail", detail)
        ));
        return new IsolationException(code, traceId, code.defaultMessage());
    }

    public IsolationException reject(IsolationErrorCode code, String traceId, String actorId, String detail) {
        String safeTrace = (traceId == null || traceId.isBlank()) ? "missing-trace" : traceId;
        sink.record(new IsolationAuditEvent(
                UUID.randomUUID(),
                Instant.now(),
                IsolationAuditEvent.ISOLATION_REJECT,
                code,
                actorId,
                null,
                null,
                code.defaultMessage(),
                safeTrace,
                detail == null ? Map.of() : Map.of("detail", detail)
        ));
        return new IsolationException(code, safeTrace, code.defaultMessage());
    }
}
