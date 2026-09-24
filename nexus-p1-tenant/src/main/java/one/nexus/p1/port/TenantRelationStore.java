package one.nexus.p1.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Parent column read model, plus the single reparent write. */
public interface TenantRelationStore {

    List<TenantLink> listActive();

    /** Sets the parent, or clears it when {@code parentTenantId} is null. Returns false when the tenant is missing. */
    boolean updateParent(UUID tenantId, UUID parentTenantId, Instant updatedAt, UUID updatedBy);

    record TenantLink(UUID id, String code, String name, String status, UUID parentTenantId) {}
}
