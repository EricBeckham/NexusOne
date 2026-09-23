package one.nexus.p3.infra.permission;

import java.util.Map;
import one.nexus.p3.port.DataScopeHook;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.engine.Predicate;
import one.nexus.permission.field.FieldPolicyProjector;
import one.nexus.permission.scope.PermissionQueryBinder;
import one.nexus.permission.surface.IsolationOnlyDecision;
import one.nexus.permission.surface.SurfaceGuard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * List / detail / write use {@link SurfaceGuard} so {@code no_grant} is deny,
 * same as flow. Tenant-only remains only when no P2 TenantContext is bound
 * (isolation-only jobs). Demo {@code tenant_member} seed may still run; it is
 * not treated as G2-complete.
 */
@Component
@Primary
@ConditionalOnClass(PermissionModule.class)
public class PermissionDataScopeHook implements DataScopeHook {
    private final PermissionModule permission;
    private final ObjectProvider<TenantMemberGrantSeeder> grantSeeder;

    @Autowired
    public PermissionDataScopeHook(
            ObjectProvider<PermissionModule> permission, ObjectProvider<TenantMemberGrantSeeder> grantSeeder) {
        this(permission.getIfAvailable(), grantSeeder);
    }

    public PermissionDataScopeHook(
            PermissionModule permission, ObjectProvider<TenantMemberGrantSeeder> grantSeeder) {
        this.permission = permission;
        this.grantSeeder = grantSeeder;
    }

    @Override
    public ScopedRead bind(String tenantId, String resource, String action) {
        if (IsolationOnlyDecision.isolationOnly() || permission == null) {
            return tenantOnly(tenantId);
        }
        TenantMemberGrantSeeder.ensureIfPresent(grantSeeder);
        Decision decision = SurfaceGuard.require(permission, resource, action);
        return new ScopedRead(PermissionQueryBinder.bind(tenantId, decision), decision);
    }

    @Override
    public Map<String, Object> project(Map<String, Object> row, Decision decision) {
        if (decision == null || decision.fields() == null || decision.fields().isEmpty()) {
            return row;
        }
        return FieldPolicyProjector.project(row, decision.fields());
    }

    private static ScopedRead tenantOnly(String tenantId) {
        return new ScopedRead(PermissionQueryBinder.bind(tenantId, Predicate.TRUE), IsolationOnlyDecision.allow());
    }
}
