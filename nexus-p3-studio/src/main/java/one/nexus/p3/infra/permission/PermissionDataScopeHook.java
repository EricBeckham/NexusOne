package one.nexus.p3.infra.permission;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.port.DataScopeHook;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.engine.Predicate;
import one.nexus.permission.field.FieldPolicyProjector;
import one.nexus.permission.scope.PermissionQueryBinder;
import one.nexus.permission.support.PermissionException;

/**
 * Uses M0-05 {@code PermissionService.scopedQuery} when a P2 tenant context is
 * issued. Falls back to tenant-only when the snapshot has no grant for this resource.
 */
@Component
@Primary
@ConditionalOnClass(PermissionModule.class)
@ConditionalOnBean(PermissionModule.class)
public class PermissionDataScopeHook implements DataScopeHook {
    private final PermissionModule permission;

    public PermissionDataScopeHook(PermissionModule permission) {
        this.permission = permission;
    }

    @Override
    public ScopedRead bind(String tenantId, String resource, String action) {
        if (TenantContextHolder.get() == null) {
            return tenantOnly(tenantId);
        }
        try {
            Decision decision = permission.service().check(resource, action);
            return new ScopedRead(PermissionQueryBinder.bind(tenantId, decision), decision);
        } catch (PermissionException ex) {
            throw ex;
        }
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
