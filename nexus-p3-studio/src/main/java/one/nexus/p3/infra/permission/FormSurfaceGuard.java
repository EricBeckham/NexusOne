package one.nexus.p3.infra.permission;

import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.surface.SurfaceGuard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * Form definition list / detail / create / update / publish use the same
 * {@link SurfaceGuard} as ledger and flow. {@code no_grant},
 * {@code explicit_deny}, and {@code tenant_boundary} are deny. Isolation-only
 * jobs with no P2 {@code TenantContext} stay tenant-only, like
 * {@link PermissionDataScopeHook}.
 */
@Component
@ConditionalOnClass(PermissionModule.class)
@ConditionalOnBean(PermissionModule.class)
public class FormSurfaceGuard {
    public static final String RESOURCE = "form";
    public static final String READ = "read";
    public static final String WRITE = "write";

    private final PermissionModule permission;
    private final ObjectProvider<TenantMemberGrantSeeder> grantSeeder;

    public FormSurfaceGuard(
            PermissionModule permission, ObjectProvider<TenantMemberGrantSeeder> grantSeeder) {
        this.permission = permission;
        this.grantSeeder = grantSeeder;
    }

    public Decision requireRead() {
        return require(READ);
    }

    public Decision requireWrite() {
        return require(WRITE);
    }

    public Decision require(String action) {
        if (TenantContextHolder.get() == null) {
            return new Decision(true, "tenant_only");
        }
        TenantMemberGrantSeeder.ensureIfPresent(grantSeeder);
        return SurfaceGuard.require(permission.service(), RESOURCE, action);
    }
}
