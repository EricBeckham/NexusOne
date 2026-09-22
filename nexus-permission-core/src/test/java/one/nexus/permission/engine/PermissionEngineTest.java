package one.nexus.permission.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Port of {@code poc/p0-02/test_permission_engine.py}. Do not rewrite the truth table.
 */
class PermissionEngineTest {
    private static final Predicate SELF = Predicate.atom("owner_id", "=", "u1");
    private static final Predicate DEPT = Predicate.atom("department_id", "=", "d1");
    private static final Predicate SHARE = Predicate.atom("id", "IN", List.of("r1"));
    private static final Predicate CONTEXT = Predicate.atom("status", "=", "active");

    private Subject subject;

    @BeforeEach
    void setUp() {
        subject = new Subject("t1", "u1", Set.of("employee"), Set.of("manager"), Set.of("d1"));
    }

    private Rule rule(String kind, String principal, Predicate scope, Map<String, FieldRule> fields) {
        return new Rule(kind, principal, "ledger", "read", scope, fields);
    }

    private Rule rule() {
        return rule("role", "employee", SELF, Map.of());
    }

    @Test
    void tenantBoundaryBeforeDenyAndGrant() {
        PolicySnapshot snapshot = new PolicySnapshot("t1", 3,
                List.of(rule("role", "employee", Predicate.TRUE, Map.of())),
                List.of(rule()),
                Map.of());
        Decision result = PermissionEngine.evaluate(subject, "t2", "ledger", "read", snapshot);
        assertEquals("tenant_boundary", result.reason());
        assertFalse(result.allowed());
    }

    @Test
    void explicitDenyOverridesAllSources() {
        PolicySnapshot snapshot = new PolicySnapshot("t1", 3,
                List.of(rule()),
                List.of(rule("department", "d1", SELF, Map.of())),
                Map.of());
        assertEquals("explicit_deny", PermissionEngine.evaluate(subject, "t1", "ledger", "read", snapshot).reason());
    }

    @Test
    void userRolePositionDepartmentAndInheritedRoleUnion() {
        List<Rule> grants = List.of(
                rule("user", "u1", SELF, Map.of()),
                rule("role", "ancestor", DEPT, Map.of()),
                rule("position", "manager", SHARE, Map.of()),
                rule("department", "d1", Predicate.atom("id", "=", "r2"), Map.of()));
        PolicySnapshot snapshot = new PolicySnapshot("t1", 1, grants, List.of(),
                Map.of("employee", Set.of("ancestor"), "ancestor", Set.of("employee")));
        Decision result = PermissionEngine.evaluate(subject, "t1", "ledger", "read", snapshot);
        assertTrue(result.allowed());
        assertEquals("or", result.scope().op());
        assertEquals(4, result.scope().args().size());
    }

    @Test
    void scopeOrShareThenIntersectContext() {
        PolicySnapshot snapshot = new PolicySnapshot("t1", 1, List.of(
                rule("role", "employee", SELF, Map.of()),
                rule("position", "manager", DEPT, Map.of())));
        Decision result = PermissionEngine.evaluate(subject, "t1", "ledger", "read", snapshot,
                List.of(SHARE), CONTEXT);
        assertEquals(Predicate.allOf(Predicate.anyOf(SELF, DEPT, SHARE), CONTEXT), result.scope());
    }

    @Test
    void noGrantDeniesEvenWithShare() {
        Decision result = PermissionEngine.evaluate(subject, "t1", "ledger", "read",
                new PolicySnapshot("t1", 1), List.of(SHARE), Predicate.TRUE);
        assertEquals("no_grant", result.reason());
    }

    @Test
    void fieldUnionAndStrictestMaskAndSecondaryAuthorization() {
        List<Rule> grants = List.of(
                rule("role", "employee", SELF, Map.of(
                        "phone", new FieldRule(true, false, Mask.FULL, true),
                        "hidden", new FieldRule(false, true))),
                rule("position", "manager", SELF, Map.of(
                        "phone", new FieldRule(true, true, Mask.PARTIAL, false))));
        Decision result = PermissionEngine.evaluate(subject, "t1", "ledger", "read",
                new PolicySnapshot("t1", 1, grants));
        assertEquals(new FieldRule(true, true, Mask.FULL, true), result.fields().get("phone"));
        assertEquals(new FieldRule(false, false), result.fields().get("hidden"));
    }

    @Test
    void emptyContextIsDenied() {
        Decision result = PermissionEngine.evaluate(subject, "t1", "ledger", "read",
                new PolicySnapshot("t1", 1, List.of(rule())), List.of(), Predicate.FALSE);
        assertEquals("empty_scope", result.reason());
    }
}
