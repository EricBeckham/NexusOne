package one.nexus.p5.connect;

import java.time.Instant;
import java.util.UUID;

/** Self-use API catalog metadata. No credentials, call volume, or success rate. */
public record ApiCatalogEntry(
        UUID id,
        UUID tenantId,
        String name,
        String httpMethod,
        String path,
        String version,
        String scope,
        String status,
        String description,
        Instant createdAt,
        Instant updatedAt) {}
