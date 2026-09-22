package one.nexus.p3.domain;

import java.time.Instant;
import java.util.UUID;

public record Application(
        UUID id,
        UUID tenantId,
        String tenantCode,
        String appCode,
        String displayName,
        ApplicationKind kind,
        ApplicationStatus status,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy,
        Instant deletedAt,
        long version
) {}
