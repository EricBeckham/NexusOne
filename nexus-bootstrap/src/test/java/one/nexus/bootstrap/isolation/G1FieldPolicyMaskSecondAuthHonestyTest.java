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
import java.util.concurrent.atomic.AtomicReference;
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
import one.nexus.permission.field.FieldPolicyMaskHonesty;
import one.nexus.permission.field.FieldPolicyProjector;
import one.nexus.permission.scope.QueryAstScopedQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * G1 leftover honesty: mask / second-auth stay beside Query AST on
 * {@code FieldPolicyProjector}. Hidden drop is already #115/#130/#134.
 * Does not redo #142 IN / #146 BoundSql COUNT. Does not invent a second
 * tenant context or change same-TX {@code AuditPort.append} joining.
 * A passing test means the leftover is still open — not a product G1 close.
 */
class G1FieldPolicyMaskSecondAuthHonestyTest {
    private final CollectingAuditSink collecting = new CollectingAuditSink();
    private final InMemoryHashChainedAuditPort port =
            new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
    private final IsolationKernel kernel;

    G1FieldPolicyMaskSecondAuthHonestyTest() {
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
    void maskAndSecondAuthStayBesideQueryAst() {
        bind("alpha");
        assertEquals("NOT_CLAIMED", FieldPolicyMaskHonesty.G1_CLOSE);
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
        assertEquals(List.of("secret"), list.encoding().droppedProjection());
        Map<String, FieldRule> leftover = FieldPolicyMaskHonesty.leftoverBesideAst(list.encoding());
        assertEquals(Mask.PARTIAL, leftover.get("phone").mask());
        assertTrue(leftover.get("phone").secondAuthorization());
        assertTrue(FieldPolicyMaskHonesty.compiledSqlLeavesMaskToProjector(list.compiled().sql(), "phone"));
        assertTrue(list.compiled().sql().contains("\"phone\""));
        assertFalse(list.compiled().sql().contains("secret"));
        assertFalse(list.compiled().sql().contains("***"));

        Map<String, Object> row = Map.of("title", "Alpha", "phone", "13812345678", "secret", "classified");
        Map<String, Object> denied = FieldPolicyProjector.project(row, fields, false);
        assertEquals("***", denied.get("phone"));
        assertFalse(denied.containsKey("secret"));
        assertEquals("138****78", FieldPolicyProjector.project(row, fields, true).get("phone"));

        AtomicBoolean fileSink = new AtomicBoolean(false);
        IsolationException second = assertThrows(IsolationException.class, () -> IsolatedExport.writeProjected(
                "alpha",
                "export/phone.csv",
                List.of("phone"),
                List.of(Map.of("phone", "13812345678")),
                ExportFieldPolicies.from(fields),
                false,
                (key, body) -> fileSink.set(true)));
        assertEquals(IsolationErrorCode.FIELD_POLICY_EXPORT, second.error());
        assertFalse(fileSink.get());

        var grantedCompile = IsolatedExport.compile(
                kernel.compiler(),
                list.encoding().query(),
                "alpha",
                ExportFieldPolicies.from(fields),
                true);
        assertTrue(FieldPolicyMaskHonesty.compiledSqlLeavesMaskToProjector(grantedCompile.sql(), "phone"));
        assertTrue(grantedCompile.sql().contains("\"owner_id\" = ?"));
        assertTrue(grantedCompile.sql().contains("\"tenant_id\" = ?"));

        AtomicReference<String> csv = new AtomicReference<>();
        IsolatedExport.writeProjected(
                "alpha",
                "export/granted.csv",
                List.of("title", "phone"),
                List.of(Map.of("title", "Alpha", "phone", "13812345678")),
                ExportFieldPolicies.from(fields),
                true,
                (key, body) -> csv.set(new String(body)));
        assertTrue(csv.get().contains("13812345678"), "IsolatedExport still writes raw cells; mask stays on projector");
        assertFalse(csv.get().contains("***"));
        assertFalse(csv.get().contains("138****78"));

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
