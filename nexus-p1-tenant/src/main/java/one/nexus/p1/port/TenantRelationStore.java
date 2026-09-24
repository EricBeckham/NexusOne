package one.nexus.p1.port;

import java.util.List;
import java.util.UUID;

/** Read model for the tenant parent column. Does not reparent tenants. */
public interface TenantRelationStore {

    List<TenantLink> listActive();

    record TenantLink(UUID id, String code, String name, String status, UUID parentTenantId) {}
}
