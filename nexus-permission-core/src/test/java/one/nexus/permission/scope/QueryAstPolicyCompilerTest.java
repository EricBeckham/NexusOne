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
import one.nexus.permission.engine.DataScopeCompiler;
import one.nexus.permission.engine.DataScopeKind;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.engine.DepartmentCatalog;
import one.nexus.permission.engine.FieldRule;
import one.nexus.permission.engine.Mask;
import one.nexus.permission.engine.Predicate;
import one.nexus.permission.engine.Subject;
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
    void encodesMultiValueInAndMixedColumnOr() {
        QueryAstPolicyCompiler.Encoding multiDept = QueryAstPolicyCompiler.encode(
                QueryAst.select("ledger_entry", List.of("id"), Map.of()),
                Predicate.atom("department_id", "IN", List.of("d1", "d2")),
                Map.of(),
                table);
        assertTrue(multiDept.scopeFullyEncoded());
        assertEquals(List.of("d1", "d2"), multiDept.query().filters().get("department_id"));
        assertEquals(Predicate.TRUE, multiDept.leftoverScope());

        QueryAstPolicyCompiler.Encoding sameColumnOr = QueryAstPolicyCompiler.encode(
                QueryAst.select("ledger_entry", List.of("id"), Map.of()),
                Predicate.anyOf(
                        Predicate.atom("department_id", "=", "d1"),
                        Predicate.atom("department_id", "=", "d2")),
                Map.of(),
                table);
        assertTrue(sameColumnOr.scopeFullyEncoded());
        assertEquals(List.of("d1", "d2"), sameColumnOr.query().filters().get("department_id"));

        Subject subject = new Subject("t1", "u1", Set.of(), Set.of(), Set.of("d1"),
                Map.of("d1", "/d1/", "d2", "/d1/d2/"));
        DepartmentCatalog catalog = new DepartmentCatalog(Map.of(
                "d1", "/d1/",
                "d2", "/d1/d2/",
                "d9", "/other/"));
        Predicate children = DataScopeCompiler.compile(DataScopeKind.DEPT_AND_CHILDREN, subject, catalog, List.of());
        QueryAstPolicyCompiler.Encoding encodedChildren = QueryAstPolicyCompiler.encode(
                QueryAst.select("ledger_entry", List.of("id"), Map.of()),
                children,
                Map.of(),
                table);
        assertTrue(encodedChildren.scopeFullyEncoded());
        assertEquals(List.of("d1", "d2"), encodedChildren.query().filters().get("department_id"));
        Predicate specified = DataScopeCompiler.compile(
                DataScopeKind.SPECIFIED, subject, catalog, List.of("d1", "d9"));
        QueryAstPolicyCompiler.Encoding encodedSpecified = QueryAstPolicyCompiler.encode(
                QueryAst.select("ledger_entry", List.of("id"), Map.of()),
                specified,
                Map.of(),
                table);
        assertTrue(encodedSpecified.scopeFullyEncoded());
        assertEquals(Set.of("d1", "d9"), Set.copyOf((List<?>) encodedSpecified.query().filters().get("department_id")));

        Predicate orScope = Predicate.anyOf(
                Predicate.atom("owner_id", "=", "u1"),
                Predicate.atom("department_id", "=", "d1"));
        QueryAstPolicyCompiler.Encoding encodedOr = QueryAstPolicyCompiler.encode(
                QueryAst.select("ledger_entry", List.of("id"), Map.of()),
                orScope,
                Map.of(),
                table);
        assertTrue(encodedOr.scopeFullyEncoded());
        assertEquals(Predicate.TRUE, encodedOr.leftoverScope());
        assertTrue(encodedOr.query().filters().isEmpty());
        assertEquals(1, encodedOr.query().disjunctions().size());
        assertEquals(
                List.of(Map.of("owner_id", "u1"), Map.of("department_id", "d1")),
                encodedOr.query().disjunctions().getFirst().branches());

        Predicate unencodable = Predicate.anyOf(
                Predicate.atom("title", "=", "x"),
                Predicate.atom("phone", "=", "1"));
        QueryAstPolicyCompiler.Encoding leftover = QueryAstPolicyCompiler.encode(
                QueryAst.select("ledger_entry", List.of("id"), Map.of()),
                unencodable,
                Map.of(),
                table);
        assertFalse(leftover.scopeFullyEncoded());
        assertEquals(unencodable, leftover.leftoverScope());
        assertTrue(leftover.query().disjunctions().isEmpty());
        ScopedQuery bound = PermissionQueryBinder.bind("t1", leftover.leftoverScope());
        assertTrue(bound.whereSql().startsWith("tenant_id = ? AND "));
        assertTrue(bound.whereSql().contains("title"));
        assertTrue(bound.whereSql().contains("phone"));

        QueryAstPolicyCompiler.Encoding emptyIn = QueryAstPolicyCompiler.encode(
                QueryAst.select("ledger_entry", List.of("id"), Map.of()),
                Predicate.atom("department_id", "IN", List.of()),
                Map.of(),
                table);
        assertFalse(emptyIn.scopeFullyEncoded());
        assertTrue(emptyIn.query().filters().isEmpty());
        ScopedQuery emptyBound = PermissionQueryBinder.bind("t1", emptyIn.leftoverScope());
        assertTrue(emptyBound.whereSql().startsWith("tenant_id = ? AND "));
        assertTrue(emptyBound.whereSql().contains("FALSE"));

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
        assertTrue(mixed.scopeFullyEncoded());
        assertEquals(Predicate.TRUE, mixed.leftoverScope());
        assertEquals(1, mixed.query().disjunctions().size());
        assertEquals(
                List.of(Map.of("department_id", "d1"), Map.of("id", List.of("r1", "r2"))),
                mixed.query().disjunctions().getFirst().branches());

        Predicate andOfOr = Predicate.allOf(
                Predicate.anyOf(
                        Predicate.atom("owner_id", "=", "u1"),
                        Predicate.atom("department_id", "=", "d1")),
                Predicate.anyOf(
                        Predicate.atom("id", "=", "r1"),
                        Predicate.atom("status", "=", "active")));
        QueryAstPolicyCompiler.Encoding encodedAndOr = QueryAstPolicyCompiler.encode(
                QueryAst.select("ledger_entry", List.of("id"), Map.of()),
                andOfOr,
                Map.of(),
                table);
        assertTrue(encodedAndOr.scopeFullyEncoded());
        assertEquals(2, encodedAndOr.query().disjunctions().size());

        Predicate leftoverAndOr = Predicate.allOf(
                Predicate.atom("owner_id", "=", "u1"),
                Predicate.anyOf(
                        Predicate.atom("title", "=", "x"),
                        Predicate.atom("phone", "=", "1")));
        QueryAstPolicyCompiler.Encoding leftover = QueryAstPolicyCompiler.encode(
                QueryAst.select("ledger_entry", List.of("id"), Map.of()),
                leftoverAndOr,
                Map.of(),
                table);
        assertEquals("u1", leftover.query().filters().get("owner_id"));
        assertFalse(leftover.scopeFullyEncoded());
        assertEquals("or", leftover.leftoverScope().op());
    }
}
