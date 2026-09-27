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
 * FA3: post-completion action config and runs. {@code process.action}/{@code read} lists, previews and shows runs;
 * {@code process.action}/{@code write} creates, updates, enables and retries. Granted to the tenant process designer
 * role (tenant admins), not to every member. Isolation-only hosts with no P2 context stay tenant-only.
 */
@Component
@ConditionalOnClass(PermissionModule.class)
@ConditionalOnBean(PermissionModule.class)
public class CompletionActionSurfaceGuard {
    public static final String RESOURCE = "process.action";
    public static final String READ = "read";
    public static final String WRITE = "write";

    private final PermissionModule permission;
    private final ObjectProvider<TenantMemberGrantSeeder> grantSeeder;

    public CompletionActionSurfaceGuard(
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

    private Decision require(String action) {
        if (IsolationOnlyDecision.isolationOnly()) {
            return IsolationOnlyDecision.allow();
        }
        TenantMemberGrantSeeder.ensureIfPresent(grantSeeder);
        return SurfaceGuard.require(permission, RESOURCE, action);
    }
}
