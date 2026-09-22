package one.nexus.p3.infra.permission;

import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.surface.H5SurfaceCatalog;
import one.nexus.permission.surface.SurfaceGuard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * H5 {@code /m/*} list / detail / form / todo interpret the same
 * {@link SurfaceGuard} Decision as desktop. {@code no_grant},
 * {@code explicit_deny}, and {@code tenant_boundary} are deny
 * ({@code P2.PERM.0003} / {@code 0002} / {@code 0001}). Isolation-only
 * hosts with no P2 {@code TenantContext} stay tenant-only.
 *
 * <p>Vue pages call {@code POST /api/v1/permission/check} first. This
 * guard is the in-process twin so tests and future H5 hosts share one
 * rule. Path {@code submit-start} stays the G18 walk.
 */
@Component
@ConditionalOnClass(PermissionModule.class)
@ConditionalOnBean(PermissionModule.class)
public class H5SurfaceGuard {
    private final PermissionModule permission;
    private final ObjectProvider<TenantMemberGrantSeeder> grantSeeder;

    public H5SurfaceGuard(
            PermissionModule permission, ObjectProvider<TenantMemberGrantSeeder> grantSeeder) {
        this.permission = permission;
        this.grantSeeder = grantSeeder;
    }

    public Decision requireListRead() {
        return require(H5SurfaceCatalog.LIST_RESOURCE, H5SurfaceCatalog.LIST_READ);
    }

    public Decision requireListWrite() {
        return require(H5SurfaceCatalog.LIST_RESOURCE, H5SurfaceCatalog.LIST_WRITE);
    }

    public Decision requireFormRead() {
        return require(H5SurfaceCatalog.FORM_RESOURCE, H5SurfaceCatalog.FORM_READ);
    }

    public Decision requireFormWrite() {
        return require(H5SurfaceCatalog.FORM_RESOURCE, H5SurfaceCatalog.FORM_WRITE);
    }

    public Decision requireTodoRead() {
        return require(H5SurfaceCatalog.TODO_RESOURCE, H5SurfaceCatalog.TODO_READ);
    }

    public Decision requireTodoApprove() {
        return require(H5SurfaceCatalog.TODO_RESOURCE, H5SurfaceCatalog.TODO_APPROVE);
    }

    public Decision requireTodoReject() {
        return require(H5SurfaceCatalog.TODO_RESOURCE, H5SurfaceCatalog.TODO_REJECT);
    }

    public Decision requireTodoWithdraw() {
        return require(H5SurfaceCatalog.INSTANCE_RESOURCE, H5SurfaceCatalog.INSTANCE_WITHDRAW);
    }

    public Decision require(String resource, String action) {
        if (TenantContextHolder.get() == null) {
            return new Decision(true, "tenant_only");
        }
        TenantMemberGrantSeeder.ensureIfPresent(grantSeeder);
        return SurfaceGuard.require(permission.service(), resource, action);
    }
}
