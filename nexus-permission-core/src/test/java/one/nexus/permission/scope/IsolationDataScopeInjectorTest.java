package one.nexus.permission.scope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.RenderedSql;
import one.nexus.isolation.api.TableDescriptor;
import one.nexus.isolation.api.TableScope;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.permission.engine.Decision;
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

    @Test
    void queryAstInjectorAndsSelfWithoutStrippingTenant() {
        TableDescriptor table = new TableDescriptor(
                "ledger_entry",
                TableScope.TENANT,
                Set.of("id", "tenant_id", "title", "owner_id"),
                "p3",
                1);
        IsolationDataScopeInjector scoped = new IsolationDataScopeInjector(
                new Decision(true, "self", Predicate.atom("owner_id", "=", "u1"), Map.of(), 1L));
        QueryAst after = scoped.inject(
                QueryAst.select("ledger_entry", List.of("id"), Map.of("tenant_id", "t1")),
                table,
                null);
        assertEquals("t1", after.filters().get("tenant_id"));
        assertEquals("u1", after.filters().get("owner_id"));
        assertThrows(PermissionException.class, () -> scoped.inject(
                QueryAst.select("ledger_entry", List.of("id"), Map.of()),
                table,
                null));
    }

    @Test
    void kernelCompilerRendersEncodedSelfThroughInjector() {
        TableRegistry registry = new TableRegistry();
        registry.registerTenant("ledger_entry", "p3", 1, "id", "tenant_id", "title", "owner_id");
        IsolationDataScopeInjector scoped = new IsolationDataScopeInjector(Map.of("owner_id", "u1"));
        IsolationKernel kernel = IsolationKernel.create(
                registry, Set.of("ops.summary"), new CollectingAuditSink(), scoped, (q, t, c) -> q);
        TenantContext alpha = kernel.tenants().issue(new AuthenticatedMembership(
                Actor.person("user-a"), "alpha", "mem-a", Set.of("alpha")), "trace-a");
        RenderedSql sql = kernel.compiler().compile(
                QueryAst.select("ledger_entry", List.of("id", "title"), Map.of()), alpha);
        assertEquals(
                "SELECT \"id\", \"title\" FROM \"ledger_entry\" WHERE \"tenant_id\"::text = ? AND \"owner_id\" = ?",
                sql.sql());
        assertEquals(List.of("alpha", "u1"), sql.parameters());
    }

    @Test
    void queryAstInjectorAndsInListWithoutStrippingTenant() {
        TableRegistry registry = new TableRegistry();
        registry.registerTenant("ledger_entry", "p3", 1, "id", "tenant_id", "title", "department_id");
        IsolationDataScopeInjector scoped = new IsolationDataScopeInjector(Map.of(
                "department_id", List.of("d1", "d2")));
        IsolationKernel kernel = IsolationKernel.create(
                registry, Set.of("ops.summary"), new CollectingAuditSink(), scoped, (q, t, c) -> q);
        TenantContext alpha = kernel.tenants().issue(new AuthenticatedMembership(
                Actor.person("user-a"), "alpha", "mem-a", Set.of("alpha")), "trace-in");
        RenderedSql sql = kernel.compiler().compile(
                QueryAst.select("ledger_entry", List.of("id"), Map.of()), alpha);
        assertEquals(
                "SELECT \"id\" FROM \"ledger_entry\" WHERE \"tenant_id\"::text = ? AND \"department_id\" IN (?, ?)",
                sql.sql());
        assertEquals(List.of("alpha", "d1", "d2"), sql.parameters());
    }
}
