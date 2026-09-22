package one.nexus.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Copy aged same-PG archive (or archive-eligible hot rows) into object storage.
 * Implementations must not UPDATE or DELETE {@code platform.audit_record}.
 */
public interface AuditColdArchivePort {
    ColdArchiveRunResult copyEligible(Instant now);

    List<String> list(String prefix);

    List<ChainedAuditEvent> restoreByTraceId(String traceId);

    ChainedAuditEvent restore(String objectKey);

    List<String> listTenant(UUID tenantId);

    List<String> listPlatform();
}
