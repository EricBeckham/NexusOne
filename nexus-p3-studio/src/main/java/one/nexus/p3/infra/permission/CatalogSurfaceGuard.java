package one.nexus.p3.infra.permission;

import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.surface.CatalogSurfaceCatalog;
import one.nexus.permission.surface.IsolationOnlyDecision;
import one.nexus.permission.surface.SurfaceGuard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * Ledger / application / business-type catalog HTTP interpret the same
 * {@link SurfaceGuard} Decision as desktop list ({@code ledger}/{@code read}
 * and {@code ledger}/{@code write}). Isolation-only hosts with no P2
 * {@code TenantContext} stay tenant-only. An issued P2 context cannot emit
 * {@code tenant_only} — it fail-closes through {@link IsolationOnlyDecision}
 * then {@link SurfaceGuard}.
 *
 * <p>Not a physical dump. Not a product-wide G2 close. Todo HTTP is the
 * sibling leftover {@link TodoSurfaceGuard}.
 */
@Component
@ConditionalOnClass(PermissionModule.class)
@ConditionalOnBean(PermissionModule.class)
public class CatalogSurfaceGuard {
    private final PermissionModule permission;
    private final ObjectProvider<TenantMemberGrantSeeder> grantSeeder;

    public CatalogSurfaceGuard(
            PermissionModule permission, ObjectProvider<TenantMemberGrantSeeder> grantSeeder) {
        this.permission = permission;
        this.grantSeeder = grantSeeder;
    }

    public Decision requireLedgerRead() {
        return require(CatalogSurfaceCatalog.LEDGER_RESOURCE, CatalogSurfaceCatalog.LEDGER_READ);
    }

    public Decision requireLedgerWrite() {
        return require(CatalogSurfaceCatalog.LEDGER_RESOURCE, CatalogSurfaceCatalog.LEDGER_WRITE);
    }

    public Decision requireApplicationRead() {
        return require(CatalogSurfaceCatalog.APPLICATION_RESOURCE, CatalogSurfaceCatalog.APPLICATION_READ);
    }

    public Decision requireApplicationWrite() {
        return require(CatalogSurfaceCatalog.APPLICATION_RESOURCE, CatalogSurfaceCatalog.APPLICATION_WRITE);
    }

    public Decision requireBusinessTypeRead() {
        return require(CatalogSurfaceCatalog.BUSINESS_TYPE_RESOURCE, CatalogSurfaceCatalog.BUSINESS_TYPE_READ);
    }

    public Decision requireBusinessTypeWrite() {
        return require(CatalogSurfaceCatalog.BUSINESS_TYPE_RESOURCE, CatalogSurfaceCatalog.BUSINESS_TYPE_WRITE);
    }

    public Decision require(String resource, String action) {
        if (IsolationOnlyDecision.isolationOnly()) {
            return IsolationOnlyDecision.allow();
        }
        TenantMemberGrantSeeder.ensureIfPresent(grantSeeder);
        return SurfaceGuard.require(permission, resource, action);
    }
}
