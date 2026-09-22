package one.nexus.p3.infra.permission;

import java.util.Map;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.port.DataScopeHook;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.engine.Predicate;
import one.nexus.permission.field.FieldPolicyProjector;
import one.nexus.permission.scope.PermissionQueryBinder;
import one.nexus.permission.support.PermissionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Uses M0-05 {@code PermissionService.decide} when a P2 tenant context is
 * issued. Falls back to tenant-only when the snapshot has no grant for this
 * resource. Explicit deny and tenant-boundary still 403.
 */
@Component
@Primary
@ConditionalOnClass(PermissionModule.class)
@ConditionalOnBean(PermissionModule.class)
public class PermissionDataScopeHook implements DataScopeHook {
    private final PermissionModule permission;
    private final ObjectProvider<TenantMemberGrantSeeder> grantSeeder;

    public PermissionDataScopeHook(
            PermissionModule permission, ObjectProvider<TenantMemberGrantSeeder> grantSeeder) {
        this.permission = permission;
        this.grantSeeder = grantSeeder;
    }

    @Override
    public ScopedRead bind(String tenantId, String resource, String action) {
        if (TenantContextHolder.get() == null) {
            return tenantOnly(tenantId);
        }
        TenantMemberGrantSeeder seeder = grantSeeder.getIfAvailable();
        if (seeder != null) {
            seeder.ensure();
        }
        Decision decision = permission.service().decide(resource, action);
        if (decision.allowed()) {
            return new ScopedRead(PermissionQueryBinder.bind(tenantId, decision), decision);
        }
        if ("explicit_deny".equals(decision.reason()) || "tenant_boundary".equals(decision.reason())) {
            throw PermissionException.denied(decision.reason());
        }
        return tenantOnly(tenantId);
    }

    @Override
    public Map<String, Object> project(Map<String, Object> row, Decision decision) {
        if (decision == null || decision.fields() == null || decision.fields().isEmpty()) {
            return row;
        }
        return FieldPolicyProjector.project(row, decision.fields());
    }

    private static ScopedRead tenantOnly(String tenantId) {
        return new ScopedRead(PermissionQueryBinder.bind(tenantId, Predicate.TRUE), new Decision(true, "tenant_only"));
    }
}
