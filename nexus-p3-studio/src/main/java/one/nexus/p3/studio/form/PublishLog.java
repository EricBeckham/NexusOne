package one.nexus.p3.studio.form;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PublishLog(
        UUID logId,
        UUID formId,
        UUID tenantId,
        Integer versionNo,
        String fromStatus,
        String toStatus,
        String outcome,
        String message,
        Map<String, Object> details,
        String traceId,
        Instant occurredAt) {}
