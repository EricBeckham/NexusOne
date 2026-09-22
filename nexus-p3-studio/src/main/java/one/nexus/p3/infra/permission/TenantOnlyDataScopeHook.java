package one.nexus.p3.infra.permission;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import one.nexus.p3.port.DataScopeHook;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.engine.Predicate;
import one.nexus.permission.scope.PermissionQueryBinder;

/** Default list/detail filter: {@code tenant_id = ? AND (TRUE)}. */
@Component
@ConditionalOnMissingBean(DataScopeHook.class)
public class TenantOnlyDataScopeHook implements DataScopeHook {
    @Override
    public ScopedRead bind(String tenantId, String resource, String action) {
        return new ScopedRead(PermissionQueryBinder.bind(tenantId, Predicate.TRUE), new Decision(true, "tenant_only"));
    }

    @Override
    public Map<String, Object> project(Map<String, Object> row, Decision decision) {
        return row;
    }
}
