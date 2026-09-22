package one.nexus.p3.infra.permission;

import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.surface.DumpSurfaceCatalog;
import one.nexus.permission.surface.SurfaceGuard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * Physical ledger dump / raw export interpret the same {@link SurfaceGuard}
 * Decision as list/detail and {@code ledger.export.csv}/{@code submit}.
 * {@code no_grant}, {@code explicit_deny}, and {@code tenant_boundary} are
 * deny ({@code P2.PERM.0003} / {@code 0002} / {@code 0001}). Isolation-only
 * hosts with no P2 {@code TenantContext} stay tenant-only.
 *
 * <p>Vue calls {@code POST /api/v1/permission/check} first; this guard is
 * the in-process twin for HTTP dump entry points. Report query/export stays
 * {@link ReportSurfaceGuard}. {@code pg_dump} ops stay off this engine.
 */
@Component
@ConditionalOnClass(PermissionModule.class)
@ConditionalOnBean(PermissionModule.class)
public class DumpSurfaceGuard {
    private final PermissionModule permission;
    private final ObjectProvider<TenantMemberGrantSeeder> grantSeeder;

    public DumpSurfaceGuard(
            PermissionModule permission, ObjectProvider<TenantMemberGrantSeeder> grantSeeder) {
        this.permission = permission;
        this.grantSeeder = grantSeeder;
    }

    public Decision requireRead() {
        return require(DumpSurfaceCatalog.READ_RESOURCE, DumpSurfaceCatalog.READ_ACTION);
    }

    public Decision requireDump() {
        return require(DumpSurfaceCatalog.DUMP_RESOURCE, DumpSurfaceCatalog.DUMP_ACTION);
    }

    public Decision decideRead() {
        return decide(DumpSurfaceCatalog.READ_RESOURCE, DumpSurfaceCatalog.READ_ACTION);
    }

    public Decision decideDump() {
        return decide(DumpSurfaceCatalog.DUMP_RESOURCE, DumpSurfaceCatalog.DUMP_ACTION);
    }

    public Decision require(String resource, String action) {
        if (TenantContextHolder.get() == null) {
            return isolationOnly();
        }
        TenantMemberGrantSeeder.ensureIfPresent(grantSeeder);
        return SurfaceGuard.require(permission, resource, action);
    }

    public Decision decide(String resource, String action) {
        if (TenantContextHolder.get() == null) {
            return isolationOnly();
        }
        TenantMemberGrantSeeder.ensureIfPresent(grantSeeder);
        Decision decision = permission.service().decide(resource, action);
        return decision == null ? new Decision(false, "no_grant") : decision;
    }

    /** Isolation-only leftover when no P2 {@code TenantContext}, same as Form/H5/report. */
    public static Decision isolationOnly() {
        return new Decision(true, "tenant_only");
    }
}
