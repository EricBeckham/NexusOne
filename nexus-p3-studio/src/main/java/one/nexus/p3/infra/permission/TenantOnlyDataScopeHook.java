package one.nexus.p3.infra.permission;

import java.util.Map;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.port.DataScopeHook;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.engine.Predicate;
import one.nexus.permission.scope.PermissionQueryBinder;
import one.nexus.permission.surface.IsolationOnlyDecision;

/**
 * Fallback list/detail filter when {@link PermissionDataScopeHook} is absent
 * ({@code tenant_id = ? AND (TRUE)}). Registered from {@code P3Configuration}.
 *
 * <p>No P2 {@code TenantContext} uses {@link IsolationOnlyDecision#allow()}.
 * An issued P2 context on this no-engine fallback is a documented leftover:
 * isolation hosts that bind demo P2 headers without {@code PermissionModule}
 * still stay tenant-filtered. Product wrappers with the engine must not call
 * this path — they fail-close through {@link IsolationOnlyDecision}.
 */
public class TenantOnlyDataScopeHook implements DataScopeHook {
    @Override
    public ScopedRead bind(String tenantId, String resource, String action) {
        Decision decision = IsolationOnlyDecision.isolationOnly()
                ? IsolationOnlyDecision.allow()
                : leftoverAllowWithIssuedContext();
        return new ScopedRead(PermissionQueryBinder.bind(tenantId, Predicate.TRUE), decision);
    }

    @Override
    public Map<String, Object> project(Map<String, Object> row, Decision decision) {
        return row;
    }

    /**
     * No-engine leftover. Must not be used by Form/H5/report/catalog wrappers
     * that can reach {@link IsolationOnlyDecision#allow()}.
     */
    static Decision leftoverAllowWithIssuedContext() {
        if (TenantContextHolder.get() == null) {
            return IsolationOnlyDecision.allow();
        }
        return new Decision(true, IsolationOnlyDecision.REASON, Predicate.TRUE, Map.of(), 0L);
    }
}
