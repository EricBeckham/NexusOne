package one.nexus.p1.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p1.domain.EntitlementProjection;
import one.nexus.p1.domain.Tenant;
import one.nexus.p1.domain.TenantConfig;
import one.nexus.p1.port.TenantAdminInitializer.AdminInitResult;

public interface TenantRepository {

    void insert(Tenant tenant, TenantConfig config, EntitlementProjection projection);

    void update(Tenant tenant);

    void updateConfig(TenantConfig config);

    Optional<Tenant> findById(UUID id);

    Optional<Tenant> findByCode(String code);

    boolean codeExists(String code);

    List<Tenant> listAll();

    Optional<TenantConfig> findConfig(UUID tenantId);

    Optional<EntitlementProjection> findProjection(UUID tenantId);

    void updateProjection(EntitlementProjection projection);

    void insertAdminInit(UUID requestId, UUID tenantId, String displayName, String email,
                         AdminInitResult.Status status, String message, UUID actorId, String traceId);
}
