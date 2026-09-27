package one.nexus.bootstrap.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import one.nexus.p1.config.P1Properties;
import one.nexus.p1.port.TenantRepository;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.domain.Model;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.PlatformDutyCatalog;
import one.nexus.permission.engine.PermissionEngine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PV2: {@code GET /api/v1/auth/products} — the products the caller may enter in the issued context
 * ({@code X-Nexus-Context-Id}). Under {@code /api/v1/auth/}, so {@code IdentityAuthFilter} has already verified the
 * Bearer token and bound the context. Answers {@code {"products": ["p3"]}}; no context → an empty list.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class ProductVisibilityController {
    private final IdentityModule identity;
    private final PermissionModule permissions;
    private final P1Properties properties;
    private final ObjectProvider<TenantRepository> tenants;

    public ProductVisibilityController(IdentityModule identity, PermissionModule permissions, P1Properties properties,
            ObjectProvider<TenantRepository> tenants) {
        this.identity = identity;
        this.permissions = permissions;
        this.properties = properties;
        this.tenants = tenants;
    }

    @GetMapping("/products")
    public Map<String, Object> products() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("products", ProductVisibility.visible(facts(TenantContextHolder.get())));
        return body;
    }

    ProductVisibility.Facts facts(TenantContext context) {
        if (context == null || context.tenantId() == null || context.personId() == null) {
            return new ProductVisibility.Facts(false, false, Set.of(), false, false);
        }
        boolean platform = properties.getPlatformTenant().getId().equals(context.tenantId());
        if (platform) {
            return new ProductVisibility.Facts(true, true, platformDuties(), false, false);
        }
        return new ProductVisibility.Facts(true, false, Set.of(), tenantAdmin(context), connectEntitled(context.tenantId()));
    }

    private Set<String> platformDuties() {
        try {
            var service = permissions.service();
            return PlatformDutyCatalog.heldDuties(PermissionEngine.expandedRoleCodes(
                    service.currentSubject(), service.snapshot()));
        } catch (RuntimeException unavailable) {
            return Set.of(); // fail closed: no platform product without a readable duty set
        }
    }

    private boolean tenantAdmin(TenantContext context) {
        var repository = identity.repository();
        UUID tenantId = context.tenantId();
        List<Model.Employment> employments = repository.listEmploymentsByPerson(tenantId, context.personId());
        return employments.stream()
                .filter(item -> item.status() == Model.EmploymentStatus.ACTIVE && item.deletedAt() == null)
                .anyMatch(item -> repository.findPosition(tenantId, item.positionId())
                        .map(position -> ProductVisibility.isTenantAdminPosition(position.code()))
                        .orElse(false));
    }

    private boolean connectEntitled(UUID tenantId) {
        TenantRepository repository = tenants.getIfAvailable();
        if (repository == null) {
            return false;
        }
        try {
            return repository.findProjection(tenantId)
                    .map(projection -> projection.payload().entitlements().get(ProductVisibility.CONNECT_ENTITLEMENT))
                    .map(item -> item.enabled())
                    .orElse(false);
        } catch (RuntimeException unavailable) {
            return false;
        }
    }
}
