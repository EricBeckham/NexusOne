package one.nexus.p5.connect;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped credential metadata. Callers must not add a plaintext column. */
public interface CredentialMetadataRepository {
    List<CredentialMetadata> list(UUID tenantId);

    Optional<CredentialMetadata> find(UUID tenantId, UUID id);

    Optional<CredentialMetadata> findByName(UUID tenantId, String name);

    void insert(CredentialMetadata row);

    boolean update(UUID tenantId, UUID id, String name, String provider, Instant expiresAt, Instant updatedAt);

    boolean markRotated(UUID tenantId, UUID id, Instant rotatedAt);

    boolean markRevoked(UUID tenantId, UUID id, Instant revokedAt);
}
