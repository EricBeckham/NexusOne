package one.nexus.audit;

import java.util.UUID;
import one.nexus.common.audit.AuditEvent;

/**
 * Object keys for cold audit copies. Matches {@code FileObjectKeyGuard}:
 * {@code tenants/{tenantId}/...} or {@code platform/...}.
 */
public final class AuditColdObjectKeys {
    public static final String RELATIVE_PREFIX = "audit/cold/";

    private AuditColdObjectKeys() {}

    public static String objectKey(ChainedAuditEvent linked) {
        return objectKey(linked.event());
    }

    public static String objectKey(AuditEvent event) {
        String relative = RELATIVE_PREFIX + event.eventId() + ".json";
        if (event.platformContext() || event.tenantId() == null) {
            return platformPrefix() + relative;
        }
        return tenantPrefix(event.tenantId()) + relative;
    }

    public static String tenantPrefix(UUID tenantId) {
        return "tenants/" + tenantId + "/";
    }

    public static String platformPrefix() {
        return "platform/";
    }
}
