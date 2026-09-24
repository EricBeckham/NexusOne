package one.nexus.p5.connect;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiCatalogRepository {
    String VISIBLE_TENANT = "(tenant_id IS NULL OR tenant_id = :tenant)";

    List<ApiCatalogEntry> listVisible(UUID tenantId);

    Optional<ApiCatalogEntry> findVisible(UUID tenantId, UUID id);
}
