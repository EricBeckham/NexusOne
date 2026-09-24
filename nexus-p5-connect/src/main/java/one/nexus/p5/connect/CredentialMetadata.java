package one.nexus.p5.connect;

import java.time.Instant;
import java.util.UUID;

/** Credential metadata. {@code secretName} is a SecretPort reference, never secret material. */
public record CredentialMetadata(
        UUID id,
        UUID tenantId,
        String name,
        String provider,
        String status,
        Instant lastRotatedAt,
        Instant expiresAt,
        String secretName,
        Instant createdAt,
        Instant updatedAt) {
    public static final String ACTIVE = "ACTIVE";
    public static final String REVOKED = "REVOKED";
}
