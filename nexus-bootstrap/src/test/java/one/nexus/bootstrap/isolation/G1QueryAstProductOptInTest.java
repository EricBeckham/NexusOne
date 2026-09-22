package one.nexus.bootstrap.isolation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.InMemoryHashChainedAuditPort;
import one.nexus.common.audit.AuditCategory;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.api.IsolationAuditSink;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.QueryPage;
import one.nexus.isolation.api.QuerySort;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.boundary.IsolatedExport;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.engine.FieldRule;
import one.nexus.permission.engine.Mask;
import one.nexus.permission.engine.Predicate;
import one.nexus.permission.field.FieldPolicyProjector;
import one.nexus.permission.scope.PermissionQueryBinder;
import one.nexus.permission.scope.QueryAstScopedQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * G1 leftover: product list/detail opt into {@link QueryAstScopedQuery} so
 * SAFE SELF/DEPT + FieldPolicy visibility compile through QueryCompiler.
 * Does not invent a second tenant context and does not change same-TX
 * {@code AuditPort.append} joining.
 */
class G1QueryAstProductOptInTest {
    private final CollectingAuditSink collecting = new CollectingAuditSink();
    private final InMemoryHashChainedAuditPort port =
            new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
    private final IsolationKernel kernel;

    G1QueryAstProductOptInTest() {
        TableRegistry registry = new TableRegistry();
        registry.registerTenant(
                "l_alpha_ticket",
                "p3",
                1,
                "id",
                "tenant_id",
                "title",
                "owner_id",
                "department_id",
                "deleted_at",
                "phone",
                "secret");
        IsolationAuditSink audit = IsolationAuditSink.tee(collecting, new AuditPortIsolationSink(port, event -> {}));
        this.kernel = IsolationKernel.create(registry, Set.of("ops.summary"), audit);
    }

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void productListDetailOptInEncodesSelfAndLeavesOrMaskBesideAst() {
        bind("alpha");
        Map<String, FieldRule> fields = Map.of(
                "id", new FieldRule(true, true),
                "title", new FieldRule(true, true),
                "phone", new FieldRule(true, false, Mask.PARTIAL, true),
                "secret", new FieldRule(false, false));
        Decision self = new Decision(true, "self", Predicate.atom("owner_id", "=", "user-alpha"), fields, 1L);

        QueryAstScopedQuery.CompiledRead list = QueryAstScopedQuery.compile(
                QueryAst.select(
                                "l_alpha_ticket",
                                List.of("id", "title", "phone", "secret"),
                                filters("deleted_at", null))
                        .withSorts(List.of(QuerySort.asc("id")))
                        .withPage(QueryPage.of(20, 0)),
                self,
                kernel.registry().require("l_alpha_ticket"),
                kernel.compiler(),
                AccessContextHolder.current());
        assertTrue(list.scopeFullyEncoded());
        assertEquals(List.of("id", "title", "phone"), list.encoding().query().projection());
        assertEquals("user-alpha", list.encoding().query().filters().get("owner_id"));
        assertFalse(list.encoding().query().filters().containsKey("tenant_id"));
        assertTrue(list.compiled().sql().contains("\"owner_id\" = ?"));
        assertTrue(list.compiled().sql().contains("\"tenant_id\" = ?"));
        assertTrue(list.compiled().sql().contains("LIMIT ? OFFSET ?"));
        assertFalse(list.compiled().sql().contains("secret"));
        assertEquals(list.compiled().sql(), list.bound().sql());

        QueryAstScopedQuery.CompiledRead detail = QueryAstScopedQuery.compile(
                QueryAst.select(
                        "l_alpha_ticket",
                        List.of("id", "title", "phone", "secret"),
                        filters("id", "r1", "deleted_at", null)),
                self,
                kernel.registry().require("l_alpha_ticket"),
                kernel.compiler(),
                AccessContextHolder.current());
        assertTrue(detail.compiled().sql().contains("\"id\" = ?"));
        assertTrue(detail.compiled().sql().contains("\"deleted_at\" IS NULL"));

        var count = QueryAstScopedQuery.count(QueryAstScopedQuery.compile(
                QueryAst.select("l_alpha_ticket", List.of("id"), filters("deleted_at", null)),
                self,
                kernel.registry().require("l_alpha_ticket"),
                kernel.compiler(),
                AccessContextHolder.current()));
        assertTrue(count.sql().startsWith("SELECT count(*) FROM ("));
        assertTrue(count.sql().contains("\"tenant_id\" = ?"));

        Predicate orScope = Predicate.anyOf(
                Predicate.atom("owner_id", "=", "user-alpha"),
                Predicate.atom("department_id", "=", "d1"));
        QueryAstScopedQuery.CompiledRead leftover = QueryAstScopedQuery.compile(
                QueryAst.select("l_alpha_ticket", List.of("id"), filters("deleted_at", null))
                        .withSorts(List.of(QuerySort.asc("id")))
                        .withPage(QueryPage.of(10, 0)),
                new Decision(true, "or", orScope, Map.of(), 1L),
                kernel.registry().require("l_alpha_ticket"),
                kernel.compiler(),
                AccessContextHolder.current());
        assertFalse(leftover.scopeFullyEncoded());
        assertTrue(leftover.bound().sql().contains("owner_id = ?"));
        int order = leftover.bound().sql().toLowerCase().indexOf(" order by ");
        assertTrue(leftover.bound().sql().indexOf("AND (") < order);
        var binder = PermissionQueryBinder.bind("alpha", leftover.encoding().leftoverScope());
        assertTrue(binder.whereSql().startsWith("tenant_id = ? AND "));

        assertTrue(list.encoding().leftoverMaskFields().containsKey("phone"));
        Map<String, Object> projected = FieldPolicyProjector.project(
                Map.of("title", "Alpha", "phone", "13812345678", "secret", "classified"),
                fields,
                false);
        assertEquals("***", projected.get("phone"));
        assertFalse(projected.containsKey("secret"));

        AtomicBoolean compileRanPastGuard = new AtomicBoolean(false);
        IsolationException hidden = assertThrows(IsolationException.class, () -> {
            IsolatedExport.compile(
                    kernel.compiler(),
                    QueryAst.select("l_alpha_ticket", List.of("id", "title", "secret"), Map.of()),
                    "alpha",
                    ExportFieldPolicies.from(fields),
                    false);
            compileRanPastGuard.set(true);
        });
        assertEquals(IsolationErrorCode.FIELD_POLICY_EXPORT, hidden.error());
        assertFalse(compileRanPastGuard.get());
        assertTrue(collecting.rejected(IsolationErrorCode.FIELD_POLICY_EXPORT));
        var rows = port.findByTraceId("trace-alpha");
        assertTrue(rows.stream().anyMatch(event ->
                event.event().category() == AuditCategory.GUARD_REJECT
                        && AuditCaptures.GUARD_REJECT.equals(event.event().eventType())
                        && "P1.M02.021".equals(event.event().resourceId())));
        assertTrue(port.verifyChain().intact());
    }

    private static Map<String, Object> filters(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put((String) pairs[i], pairs[i + 1]);
        }
        return result;
    }

    private void bind(String tenantId) {
        AccessContextHolder.set(kernel.tenants().issue(
                new AuthenticatedMembership(
                        Actor.person("user-" + tenantId), tenantId, "mem-" + tenantId, Set.of(tenantId)),
                "trace-" + tenantId));
    }
}
