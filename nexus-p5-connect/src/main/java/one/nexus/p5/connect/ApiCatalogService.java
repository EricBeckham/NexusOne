package one.nexus.p5.connect;

import java.util.List;
import java.util.UUID;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.context.AccessContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Read-only self-use API catalog. Shared rows are visible to every issued tenant. */
@Service
public class ApiCatalogService {
    private final ApiCatalogRepository catalog;

    public ApiCatalogService(ApiCatalogRepository catalog) {
        this.catalog = catalog;
    }

    public List<ApiCatalogEntry> list() {
        UUID tenantId = requireTenantId();
        return catalog.listVisible(tenantId).stream()
                .filter(entry -> visible(tenantId, entry.tenantId()))
                .toList();
    }

    public ApiCatalogEntry get(UUID id) {
        UUID tenantId = requireTenantId();
        return catalog.findVisible(tenantId, id)
                .filter(entry -> visible(tenantId, entry.tenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API catalog entry not found"));
    }

    static boolean visible(UUID caller, UUID owner) {
        return owner == null || owner.equals(caller);
    }

    private static UUID requireTenantId() {
        AccessContext context = AccessContextHolder.currentOrNull();
        if (!(context instanceof TenantContext tenant)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TenantContext is required");
        }
        try {
            return UUID.fromString(tenant.tenantId());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TenantContext has an invalid tenant id");
        }
    }
}
