package one.nexus.permission.scope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import one.nexus.permission.engine.Predicate;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.Test;

class IsolationDataScopeInjectorTest {
    private final IsolationDataScopeInjector injector = new IsolationDataScopeInjector();

    @Test
    void andsExtraFiltersWithoutStrippingTenant() {
        Map<String, Object> afterTenant = new LinkedHashMap<>();
        afterTenant.put("tenant_id", "t1");
        Map<String, Object> injected = injector.inject(afterTenant, Map.of("owner_id", "u1"));
        assertEquals("t1", injected.get("tenant_id"));
        assertEquals("u1", injected.get("owner_id"));
    }

    @Test
    void refusesMissingOrReplacedTenant() {
        assertThrows(PermissionException.class, () -> injector.inject(Map.of("id", "1"), Map.of()));
        Map<String, Object> afterTenant = Map.of("tenant_id", "t1");
        assertThrows(PermissionException.class,
                () -> injector.inject(afterTenant, Map.of("tenant_id", "t2")));
    }

    @Test
    void andPreserveTenantRejectsSqlWithoutTenantPredicate() {
        assertThrows(PermissionException.class,
                () -> PermissionQueryBinder.andPreserveTenant("SELECT id FROM ledger", List.of(), Predicate.TRUE));
        BoundSql bound = PermissionQueryBinder.andPreserveTenant(
                "SELECT id FROM ledger WHERE tenant_id = ?", List.of("t1"),
                Predicate.atom("owner_id", "=", "u1"));
        assertEquals("SELECT id FROM ledger WHERE tenant_id = ? AND (owner_id = ?)", bound.sql());
        assertEquals(List.of("t1", "u1"), bound.parameters());
    }
}
