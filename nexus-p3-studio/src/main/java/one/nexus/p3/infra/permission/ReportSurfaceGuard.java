package one.nexus.p3.infra.permission;

import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.surface.ReportSurfaceCatalog;
import one.nexus.permission.surface.SurfaceGuard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * Report query / export interpret the same {@link SurfaceGuard} Decision as
 * list/detail and {@code ledger.export.csv}/{@code submit}. {@code no_grant},
 * {@code explicit_deny}, and {@code tenant_boundary} are deny
 * ({@code P2.PERM.0003} / {@code 0002} / {@code 0001}). Isolation-only
 * hosts with no P2 {@code TenantContext} stay tenant-only.
 *
 * <p>Physical dump stays off this engine. Vue calls
 * {@code POST /api/v1/permission/check} first; this guard is the in-process
 * twin for HTTP report entry points.
 */
@Component
@ConditionalOnClass(PermissionModule.class)
@ConditionalOnBean(PermissionModule.class)
public class ReportSurfaceGuard {
    private final PermissionModule permission;
    private final ObjectProvider<TenantMemberGrantSeeder> grantSeeder;

    public ReportSurfaceGuard(
            PermissionModule permission, ObjectProvider<TenantMemberGrantSeeder> grantSeeder) {
        this.permission = permission;
        this.grantSeeder = grantSeeder;
    }

    public Decision requireQuery() {
        return require(ReportSurfaceCatalog.QUERY_RESOURCE, ReportSurfaceCatalog.QUERY_ACTION);
    }

    public Decision requireExport() {
        return require(ReportSurfaceCatalog.EXPORT_RESOURCE, ReportSurfaceCatalog.EXPORT_ACTION);
    }

    public Decision decideQuery() {
        return decide(ReportSurfaceCatalog.QUERY_RESOURCE, ReportSurfaceCatalog.QUERY_ACTION);
    }

    public Decision decideExport() {
        return decide(ReportSurfaceCatalog.EXPORT_RESOURCE, ReportSurfaceCatalog.EXPORT_ACTION);
    }

    public Decision require(String resource, String action) {
        return SurfaceGuard.requireAllowed(decide(resource, action));
    }

    public Decision decide(String resource, String action) {
        if (TenantContextHolder.get() == null) {
            return isolationOnly();
        }
        TenantMemberGrantSeeder.ensureIfPresent(grantSeeder);
        Decision decision = permission.service().decide(resource, action);
        return decision == null ? new Decision(false, "no_grant") : decision;
    }

    /** Isolation-only leftover when no P2 {@code TenantContext}, same as Form/H5. */
    public static Decision isolationOnly() {
        return new Decision(true, "tenant_only");
    }
}
