package one.nexus.p3.infra.permission;

import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.surface.IsolationOnlyDecision;
import one.nexus.permission.surface.SurfaceGuard;
import one.nexus.permission.surface.TodoSurfaceCatalog;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * Todo list / detail HTTP interpret the same {@link SurfaceGuard} Decision as
 * H5 {@code process.task}/{@code read}. {@code no_grant}, {@code explicit_deny},
 * and {@code tenant_boundary} are deny ({@code P2.PERM.0003} / {@code 0002} /
 * {@code 0001}). Isolation-only hosts with no P2 {@code TenantContext} stay
 * tenant-only via {@link IsolationOnlyDecision}. An issued P2 context cannot
 * emit {@code tenant_only}.
 *
 * <p>Vue H5 still calls {@code POST /api/v1/permission/check} first; this
 * guard is the in-process twin so {@code GET /api/v1/p3/todos} (and aliases)
 * cannot skip the engine. Approve / reject / withdraw stay
 * {@link one.nexus.p3.flow.SequentialApprovalService}. Not a product-wide G2
 * close. Catalog / isolation-only honesty is the sibling leftover.
 */
@Component
@ConditionalOnClass(PermissionModule.class)
@ConditionalOnBean(PermissionModule.class)
public class TodoSurfaceGuard {
    private final PermissionModule permission;
    private final ObjectProvider<TenantMemberGrantSeeder> grantSeeder;

    public TodoSurfaceGuard(
            PermissionModule permission, ObjectProvider<TenantMemberGrantSeeder> grantSeeder) {
        this.permission = permission;
        this.grantSeeder = grantSeeder;
    }

    public Decision requireTodoRead() {
        return require(TodoSurfaceCatalog.TODO_RESOURCE, TodoSurfaceCatalog.TODO_READ);
    }

    public Decision require(String resource, String action) {
        if (IsolationOnlyDecision.isolationOnly()) {
            return IsolationOnlyDecision.allow();
        }
        TenantMemberGrantSeeder.ensureIfPresent(grantSeeder);
        return SurfaceGuard.require(permission, resource, action);
    }
}
