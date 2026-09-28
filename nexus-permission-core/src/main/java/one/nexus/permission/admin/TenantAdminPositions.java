package one.nexus.permission.admin;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.domain.Model;
import one.nexus.p2.kernel.TenantContext;

/**
 * The tenant-admin position test shared by the permission module: an ACTIVE employment in a position coded
 * {@code TENANT_ADMIN} or {@code demo_tenant_admin} in the context's tenant. Same codes as {@code MarketService} /
 * {@code AdminMfaPolicy}; platform duties are separate and never implied by this.
 */
public final class TenantAdminPositions {
    public static final Set<String> CODES = Set.of("tenant_admin", "demo_tenant_admin");

    private TenantAdminPositions() {}

    public static boolean isAdminCode(String positionCode) {
        return positionCode != null && CODES.contains(positionCode.toLowerCase(Locale.ROOT));
    }

    public static boolean holds(IdentityRepository identity, TenantContext context) {
        if (identity == null || context == null || context.tenantId() == null || context.personId() == null) {
            return false;
        }
        UUID tenantId = context.tenantId();
        return identity.listEmploymentsByPerson(tenantId, context.personId()).stream()
                .filter(item -> item.status() == Model.EmploymentStatus.ACTIVE && item.deletedAt() == null)
                .anyMatch(item -> identity.findPosition(tenantId, item.positionId())
                        .map(position -> isAdminCode(position.code()))
                        .orElse(false));
    }
}
