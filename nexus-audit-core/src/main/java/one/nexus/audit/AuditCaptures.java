package one.nexus.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import one.nexus.common.audit.AuditCategory;
import one.nexus.common.audit.AuditEvent;
import one.nexus.common.id.UuidV7;
import one.nexus.common.time.Utc;
import one.nexus.common.trace.TraceIds;

/**
 * Capture factories for the M0-11 event shapes. Details are redacted here so
 * callers cannot accidentally hash a password into the chain.
 */
public final class AuditCaptures {
    public static final String LOGIN_ACCEPTED = "p2.auth.login.accepted";
    public static final String LOGIN_REJECTED = "p2.auth.login.rejected";
    public static final String LOGIN_LOGOUT = "p2.auth.logout";
    public static final String PERMISSION_DENIED = "p2.permission.denied";
    public static final String PERMISSION_GRANTED = "p2.permission.granted";
    public static final String ADMIN_OPERATION = "p1.admin.operation";
    public static final String DATA_CHANGED = "p3.data.changed";
    public static final String APPROVAL_ACTION = "p3.approval.action";
    public static final String GUARD_REJECT = "isolation.guard.reject";

    private AuditCaptures() {}

    public static AuditEvent login(
            boolean accepted,
            UUID tenantId,
            UUID actorSubjectId,
            String resourceId,
            String reason,
            String traceId,
            Map<String, ?> details) {
        return capture(
                accepted ? AuditEvent.OUTCOME_ACCEPTED : AuditEvent.OUTCOME_REJECTED,
                AuditCategory.LOGIN,
                accepted ? LOGIN_ACCEPTED : LOGIN_REJECTED,
                tenantId,
                actorSubjectId,
                tenantId,
                false,
                reason,
                traceId,
                "session",
                resourceId,
                details);
    }

    public static AuditEvent permission(
            boolean granted,
            UUID tenantId,
            UUID actorSubjectId,
            String resourceType,
            String resourceId,
            String reason,
            String traceId,
            Map<String, ?> details) {
        return capture(
                granted ? AuditEvent.OUTCOME_ACCEPTED : AuditEvent.OUTCOME_REJECTED,
                AuditCategory.PERMISSION,
                granted ? PERMISSION_GRANTED : PERMISSION_DENIED,
                tenantId,
                actorSubjectId,
                tenantId,
                false,
                reason,
                traceId,
                resourceType,
                resourceId,
                details);
    }

    public static AuditEvent admin(
            String eventType,
            boolean accepted,
            UUID tenantId,
            UUID actorSubjectId,
            UUID actorTenantId,
            String resourceType,
            String resourceId,
            String reason,
            String traceId,
            Map<String, ?> details) {
        return capture(
                accepted ? AuditEvent.OUTCOME_ACCEPTED : AuditEvent.OUTCOME_REJECTED,
                AuditCategory.ADMIN,
                eventType == null ? ADMIN_OPERATION : eventType,
                tenantId,
                actorSubjectId,
                actorTenantId,
                true,
                reason,
                traceId,
                resourceType,
                resourceId,
                details);
    }

    public static AuditEvent data(
            String eventType,
            boolean accepted,
            UUID tenantId,
            UUID actorSubjectId,
            String resourceType,
            String resourceId,
            String reason,
            String traceId,
            Map<String, ?> details) {
        return capture(
                accepted ? AuditEvent.OUTCOME_ACCEPTED : AuditEvent.OUTCOME_REJECTED,
                AuditCategory.DATA,
                eventType == null ? DATA_CHANGED : eventType,
                tenantId,
                actorSubjectId,
                tenantId,
                false,
                reason,
                traceId,
                resourceType,
                resourceId,
                details);
    }

    public static AuditEvent approval(
            String eventType,
            boolean accepted,
            UUID tenantId,
            UUID actorSubjectId,
            String resourceId,
            String reason,
            String traceId,
            Map<String, ?> details) {
        return capture(
                accepted ? AuditEvent.OUTCOME_ACCEPTED : AuditEvent.OUTCOME_REJECTED,
                AuditCategory.APPROVAL,
                eventType == null ? APPROVAL_ACTION : eventType,
                tenantId,
                actorSubjectId,
                tenantId,
                false,
                reason,
                traceId,
                "approval",
                resourceId,
                details);
    }

    public static AuditEvent guardReject(GuardRejectCapture capture) {
        return capture(
                AuditEvent.OUTCOME_REJECTED,
                AuditCategory.GUARD_REJECT,
                GUARD_REJECT,
                parseUuid(capture.tenantId()),
                parseUuid(capture.actorId()),
                parseUuid(capture.tenantId()),
                capture.platformPath() != null && !capture.platformPath().isBlank(),
                capture.reason(),
                capture.traceId(),
                "isolation",
                capture.errorCode(),
                capture.details());
    }

    public static AuditEvent capture(
            String outcome,
            AuditCategory category,
            String eventType,
            UUID tenantId,
            UUID actorSubjectId,
            UUID actorTenantId,
            boolean platformContext,
            String reason,
            String traceId,
            String resourceType,
            String resourceId,
            Map<String, ?> details) {
        String trace = (traceId == null || traceId.isBlank()) ? TraceIds.currentOrUnknown() : traceId;
        Instant now = Utc.now();
        return new AuditEvent(
                UuidV7.generate(),
                AuditEvent.VERSION,
                now,
                category,
                eventType,
                tenantId,
                actorSubjectId,
                actorTenantId,
                platformContext,
                reason,
                trace,
                TraceIds.correlationOrTrace(trace),
                null,
                outcome,
                resourceType,
                resourceId,
                SensitiveRedactor.redact(details));
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
