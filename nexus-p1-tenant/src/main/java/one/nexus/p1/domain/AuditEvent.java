package one.nexus.p1.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Shared audit event shape for M0-11. Hash chain / CryptoPort are not applied here.
 */
public record AuditEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID tenantId,
        UUID actorSubjectId,
        UUID actorTenantId,
        boolean platformContext,
        String reason,
        String traceId,
        String correlationId,
        String causationId,
        String outcome,
        String resourceType,
        String resourceId,
        Map<String, Object> details
) {

    public static final int VERSION = 1;
    public static final String OUTCOME_ACCEPTED = "ACCEPTED";
    public static final String OUTCOME_REJECTED = "REJECTED";

    public static final String TENANT_CREATED = "p1.tenant.created";
    public static final String TENANT_ENABLED = "p1.tenant.enabled";
    public static final String TENANT_DISABLED = "p1.tenant.disabled";
    public static final String TENANT_DISABLE_REJECTED = "p1.tenant.disable.rejected";
    public static final String TENANT_CONFIG_UPDATED = "p1.tenant.config.updated";
    public static final String TENANT_PARENT_UPDATED = "p1.tenant.parent.updated";
    public static final String TENANT_PARENT_REJECTED = "p1.tenant.parent.rejected";
    public static final String ADMIN_INIT_ACCEPTED = "p1.admin.init.accepted";
    public static final String QUOTA_CONSUMED = "p1.quota.consumed";
    public static final String QUOTA_RELEASED = "p1.quota.released";
    public static final String QUOTA_REJECTED = "p1.quota.rejected";
    public static final String PLATFORM_CONTEXT_USED = "p1.platform_context.used";

    public AuditEvent {
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
