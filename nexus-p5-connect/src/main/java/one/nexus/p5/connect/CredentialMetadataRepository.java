package one.nexus.p5.connect;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped credential metadata. Callers must not add a plaintext column. */
public interface CredentialMetadataRepository {
    Optional<CredentialMetadata> find(UUID tenantId, UUID id);

    boolean markRotated(UUID tenantId, UUID id, Instant rotatedAt);

    boolean markRevoked(UUID tenantId, UUID id, Instant revokedAt);
}
