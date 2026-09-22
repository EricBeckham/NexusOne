package one.nexus.permission.scope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.TableDescriptor;
import one.nexus.isolation.api.TableScope;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.engine.FieldRule;
import one.nexus.permission.engine.Mask;
import one.nexus.permission.engine.Predicate;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.Test;

class QueryAstPolicyCompilerTest {
    private final TableDescriptor table = new TableDescriptor(
            "ledger_entry",
            TableScope.TENANT,
            Set.of("id", "tenant_id", "title", "owner_id", "department_id", "status", "secret", "phone"),
            "p3",
            1);

    @Test
    void encodesSelfAndDeptEqualityAndDropsHiddenProjection() {
        Decision self = new Decision(
                true,
                "self",
                Predicate.atom("owner_id", "=", "u1"),
                Map.of(
                        "title", new FieldRule(true, true),
                        "phone", new FieldRule(true, false, Mask.PARTIAL, true),
                        "secret", new FieldRule(false, false)),
                1L);
        QueryAstPolicyCompiler.Encoding encoded = QueryAstPolicyCompiler.encode(
                QueryAst.select("ledger_entry", List.of("id", "title", "phone", "secret"), Map.of()),
                self,
                table);
        assertEquals("u1", encoded.query().filters().get("owner_id"));
        assertFalse(encoded.query().filters().containsKey("tenant_id"));
        assertEquals(List.of("id", "title", "phone"), encoded.query().projection());
        assertEquals(List.of("secret"), encoded.droppedProjection());
        assertTrue(encoded.scopeFullyEncoded());
        assertEquals(Predicate.TRUE, encoded.leftoverScope());
        assertTrue(encoded.visibilityEncoded());
        assertEquals(Mask.PARTIAL, encoded.leftoverMaskFields().get("phone").mask());
        assertTrue(encoded.leftoverMaskFields().get("phone").secondAuthorization());
        assertFalse(encoded.leftoverMaskFields().containsKey("secret"));

        Decision dept = new Decision(true, "dept", Predicate.atom("department_id", "=", "d1"), Map.of(), 1L);
        assertEquals("d1", QueryAstPolicyCompiler.encode(
                QueryAst.select("ledger_entry", List.of("id"), Map.of()), dept, table)
                .query().filters().get("department_id"));
    }

    @Test
    void leavesOrAndMultiValueInOnBinderAndRejectsTenantAuthorship() {
        Predicate orScope = Predicate.anyOf(
                Predicate.atom("owner_id", "=", "u1"),
                Predicate.atom("department_id", "=", "d1"));
        QueryAstPolicyCompiler.Encoding leftover = QueryAstPolicyCompiler.encode(
                QueryAst.select("ledger_entry", List.of("id"), Map.of()),
                orScope,
                Map.of(),
                table);
        assertFalse(leftover.scopeFullyEncoded());
        assertEquals(orScope, leftover.leftoverScope());
        assertTrue(leftover.query().filters().isEmpty());
        ScopedQuery bound = PermissionQueryBinder.bind("t1", leftover.leftoverScope());
        assertTrue(bound.whereSql().startsWith("tenant_id = ? AND "));
        assertTrue(bound.whereSql().contains("owner_id"));

        QueryAstPolicyCompiler.Encoding multiDept = QueryAstPolicyCompiler.encode(
                QueryAst.select("ledger_entry", List.of("id"), Map.of()),
                Predicate.atom("department_id", "IN", List.of("d1", "d2")),
                Map.of(),
                table);
        assertFalse(multiDept.scopeFullyEncoded());
        assertTrue(multiDept.query().filters().isEmpty());

        assertThrows(PermissionException.class, () -> QueryAstPolicyCompiler.encode(
                QueryAst.select("ledger_entry", List.of("id"), Map.of()),
                Predicate.atom("tenant_id", "=", "beta"),
                Map.of(),
                table));
        assertThrows(PermissionException.class, () -> QueryAstPolicyCompiler.encode(
                QueryAst.select("ledger_entry", List.of("secret"), Map.of()),
                Predicate.TRUE,
                Map.of("secret", new FieldRule(false, false)),
                table));
        QueryAstPolicyCompiler.Encoding denied = QueryAstPolicyCompiler.encode(
                QueryAst.select("ledger_entry", List.of("id"), Map.of()),
                new Decision(false, "no_grant"),
                table);
        assertEquals(Predicate.FALSE, denied.leftoverScope());
        assertTrue(denied.query().filters().isEmpty());
    }

    @Test
    void andsSafeAtomsAndKeepsUnsafeRemainder() {
        QueryAstPolicyCompiler.Encoding mixed = QueryAstPolicyCompiler.encode(
                QueryAst.select("ledger_entry", List.of("id"), Map.of("status", "active")),
                Predicate.allOf(
                        Predicate.atom("owner_id", "=", "u1"),
                        Predicate.anyOf(
                                Predicate.atom("department_id", "=", "d1"),
                                Predicate.atom("id", "IN", List.of("r1", "r2")))),
                Map.of(),
                table);
        assertEquals("u1", mixed.query().filters().get("owner_id"));
        assertEquals("active", mixed.query().filters().get("status"));
        assertFalse(mixed.scopeFullyEncoded());
        assertEquals("or", mixed.leftoverScope().op());
    }
}
