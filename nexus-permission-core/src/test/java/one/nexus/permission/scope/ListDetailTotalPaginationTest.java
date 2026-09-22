package one.nexus.permission.scope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.engine.FieldRule;
import one.nexus.permission.engine.Mask;
import one.nexus.permission.engine.PermissionEngine;
import one.nexus.permission.engine.PolicySnapshot;
import one.nexus.permission.engine.Predicate;
import one.nexus.permission.engine.Rule;
import one.nexus.permission.engine.Subject;
import one.nexus.permission.field.FieldPolicyProjector;
import org.junit.jupiter.api.Test;

class ListDetailTotalPaginationTest {
    private static final Predicate SELF = Predicate.atom("owner_id", "=", "u1");
    private static final Predicate DEPT = Predicate.atom("department_id", "=", "d1");
    private static final Predicate SHARE = Predicate.atom("id", "IN", List.of("r1"));
    private static final Predicate CONTEXT = Predicate.atom("status", "=", "active");

    @Test
    void listDetailTotalShareTheSameWhereAndNeverDropTenant() {
        Subject subject = new Subject("t1", "u1", java.util.Set.of("employee"), java.util.Set.of("manager"),
                java.util.Set.of("d1"));
        PolicySnapshot snapshot = new PolicySnapshot("t1", 1, List.of(
                new Rule("role", "employee", "ledger", "read", SELF, java.util.Map.of(
                        "phone", new FieldRule(true, false, Mask.FULL, false))),
                new Rule("position", "manager", "ledger", "read", DEPT, java.util.Map.of())));
        Decision decision = PermissionEngine.evaluate(subject, "t1", "ledger", "read", snapshot,
                List.of(SHARE), CONTEXT);
        ScopedQuery scoped = PermissionQueryBinder.bind("t1", decision);
        assertTrue(scoped.whereSql().startsWith("tenant_id = ? AND "));
        BoundSql count = scoped.count("ledger_row");
        BoundSql list = scoped.list("ledger_row", List.of("id", "title", "phone"), "id", 1, 0);
        BoundSql page2 = scoped.list("ledger_row", List.of("id", "title", "phone"), "id", 1, 1);
        BoundSql detail = scoped.detail("ledger_row", List.of("id", "title", "phone"), "id", "r1");
        assertEquals(count.sql().substring(count.sql().indexOf("WHERE")),
                list.sql().substring(list.sql().indexOf("WHERE"), list.sql().indexOf(" ORDER")));
        assertEquals(count.parameters(), list.parameters().subList(0, count.parameters().size()));
        assertEquals(count.parameters(), page2.parameters().subList(0, count.parameters().size()));
        assertEquals(count.parameters(), detail.parameters().subList(0, count.parameters().size()));
        assertTrue(list.sql().contains("LIMIT ? OFFSET ?"));
        var projected = FieldPolicyProjector.project(
                java.util.Map.of("id", "r1", "title", "Alpha", "phone", "13800001111"), decision.fields());
        assertEquals("***", projected.get("phone"));
        assertEquals("Alpha", projected.get("title"));
    }
}
