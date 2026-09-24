package one.nexus.p3.infra.permission;

import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.surface.IsolationOnlyDecision;
import one.nexus.permission.surface.SurfaceGuard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * Process-definition draft, validate, and publish HTTP use the same
 * {@link SurfaceGuard} Decision. List / get / publication are
 * {@code process.definition}/{@code read}. Save, validate, and publish are
 * {@code process.definition}/{@code write}. {@code no_grant},
 * {@code explicit_deny}, and {@code tenant_boundary} are deny. Isolation-only
 * hosts with no P2 {@code TenantContext} stay tenant-only.
 *
 * <p>Does not replace the draft store's tenant filter. Not a product-wide G2 close.
 */
@Component
@ConditionalOnClass(PermissionModule.class)
@ConditionalOnBean(PermissionModule.class)
public class ProcessDefinitionSurfaceGuard {
    public static final String RESOURCE = "process.definition";
    public static final String READ = "read";
    public static final String WRITE = "write";

    private final PermissionModule permission;
    private final ObjectProvider<TenantMemberGrantSeeder> grantSeeder;

    public ProcessDefinitionSurfaceGuard(
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
        if (IsolationOnlyDecision.isolationOnly()) {
            return IsolationOnlyDecision.allow();
        }
        TenantMemberGrantSeeder.ensureIfPresent(grantSeeder);
        return SurfaceGuard.require(permission, RESOURCE, action);
    }
}
