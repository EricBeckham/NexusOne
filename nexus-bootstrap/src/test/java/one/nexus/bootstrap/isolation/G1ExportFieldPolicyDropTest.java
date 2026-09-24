package one.nexus.bootstrap.isolation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.boundary.IsolatedExport;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.engine.FieldRule;
import one.nexus.permission.engine.Mask;
import one.nexus.permission.engine.Predicate;
import one.nexus.permission.field.FieldPolicyProjector;
import one.nexus.permission.scope.QueryAstPolicyCompiler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * G1 leftover: export-path FieldPolicy drops hidden Query AST columns
 * instead of rejecting. Second-auth / mask stay on
 * {@code FieldPolicyProjector}. Does not invent a second tenant context
 * and does not change same-TX {@code AuditPort.append} joining.
 */
class G1ExportFieldPolicyDropTest {
    private final CollectingAuditSink collecting = new CollectingAuditSink();
    private final InMemoryHashChainedAuditPort port =
            new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
    private final IsolationKernel kernel;

    G1ExportFieldPolicyDropTest() {
        TableRegistry registry = new TableRegistry();
        registry.registerTenant(
                "ledger_entry", "p3", 1, "id", "tenant_id", "title", "owner_id", "phone", "secret");
        this.kernel = IsolationKernel.create(
                registry,
                Set.of("ops.summary"),
                IsolationAuditSink.tee(collecting, new AuditPortIsolationSink(port, event -> {})));
    }

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void exportDropsHiddenQueryAstColumnsAndKeepsSecondAuthMaskBesideAst() {
        bind("alpha");
        AtomicBoolean fileSink = new AtomicBoolean(false);
        Map<String, FieldRule> fields = Map.of(
                "id", new FieldRule(true, true),
                "title", new FieldRule(true, true),
                "phone", new FieldRule(true, false, Mask.PARTIAL, true),
                "secret", new FieldRule(false, false));
        Decision self = new Decision(true, "self", Predicate.atom("owner_id", "=", "user-alpha"), fields, 1L);
        QueryAstPolicyCompiler.Encoding encoded = QueryAstPolicyCompiler.encode(
                QueryAst.select("ledger_entry", List.of("id", "title", "phone", "secret"), Map.of()),
                self,
                kernel.registry().require("ledger_entry"));
        assertEquals(List.of("id", "title", "phone"), encoded.query().projection());
        assertEquals(List.of("secret"), encoded.droppedProjection());
        assertTrue(encoded.leftoverMaskFields().containsKey("phone"));

        var compiled = IsolatedExport.compile(
                kernel.compiler(),
                QueryAst.select("ledger_entry", List.of("id", "title", "secret"), Map.of()),
                "alpha",
                ExportFieldPolicies.from(fields),
                false);
        assertTrue(compiled.sql().startsWith("SELECT \"id\", \"title\" FROM \"ledger_entry\""));
        assertFalse(compiled.sql().contains("secret"));
        assertTrue(compiled.sql().contains("\"tenant_id\"::text = ?"));

        var encodedCompile = IsolatedExport.compile(
                kernel.compiler(),
                encoded.query(),
                "alpha",
                ExportFieldPolicies.from(fields),
                true);
        assertTrue(encodedCompile.sql().contains("\"owner_id\" = ?"));
        assertFalse(encodedCompile.sql().contains("secret"));

        IsolationException hiddenOnly = assertThrows(IsolationException.class, () -> IsolatedExport.compile(
                kernel.compiler(),
                QueryAst.select("ledger_entry", List.of("secret"), Map.of()),
                "alpha",
                ExportFieldPolicies.from(fields),
                false));
        assertEquals(IsolationErrorCode.FIELD_POLICY_EXPORT, hiddenOnly.error());

        IsolationException second = assertThrows(IsolationException.class, () -> IsolatedExport.writeProjected(
                "alpha",
                "export/x.csv",
                List.of("phone"),
                List.of(Map.of("phone", "13812345678")),
                ExportFieldPolicies.from(fields),
                false,
                (key, body) -> fileSink.set(true)));
        assertEquals(IsolationErrorCode.FIELD_POLICY_EXPORT, second.error());

        AtomicReference<String> csv = new AtomicReference<>();
        IsolatedExport.writeProjected(
                "alpha",
                "export/ok.csv",
                List.of("title", "secret"),
                List.of(Map.of("title", "Alpha", "secret", "classified")),
                ExportFieldPolicies.from(fields),
                false,
                (key, body) -> {
                    assertEquals("tenants/alpha/export/ok.csv", key);
                    csv.set(new String(body));
                });
        assertTrue(csv.get().contains("Alpha"));
        assertFalse(csv.get().contains("classified"));

        Map<String, Object> projected = FieldPolicyProjector.project(
                Map.of("title", "Alpha", "phone", "13812345678", "secret", "classified"),
                fields,
                false);
        assertEquals("***", projected.get("phone"));
        assertFalse(projected.containsKey("secret"));

        assertFalse(fileSink.get());
        assertTrue(collecting.rejected(IsolationErrorCode.FIELD_POLICY_EXPORT));
        var rows = port.findByTraceId("trace-alpha");
        assertTrue(rows.stream().anyMatch(event ->
                event.event().category() == AuditCategory.GUARD_REJECT
                        && AuditCaptures.GUARD_REJECT.equals(event.event().eventType())
                        && "P1.M02.021".equals(event.event().resourceId())));
        assertTrue(port.verifyChain().intact());
    }

    private void bind(String tenantId) {
        AccessContextHolder.set(kernel.tenants().issue(
                new AuthenticatedMembership(
                        Actor.person("user-" + tenantId), tenantId, "mem-" + tenantId, Set.of(tenantId)),
                "trace-" + tenantId));
    }
}
