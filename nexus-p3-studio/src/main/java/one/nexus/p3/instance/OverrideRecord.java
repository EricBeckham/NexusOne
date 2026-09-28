package one.nexus.p3.instance;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Tenant override. {@code content} is not written to logs. */
public record OverrideRecord(
        UUID tenantId,
        UUID instanceId,
        ObjectType objectType,
        String objectCode,
        String baseVersion,
        Map<String, Object> content,
        Instant updatedAt,
        String updatedBy
) {}
