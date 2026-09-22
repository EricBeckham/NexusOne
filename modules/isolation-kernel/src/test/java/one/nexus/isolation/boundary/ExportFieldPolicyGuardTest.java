package one.nexus.isolation.boundary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.api.ExportFieldRule;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ExportFieldPolicyGuardTest {
    private final CollectingAuditSink sink = new CollectingAuditSink();
    private final IsolationKernel kernel = IsolationKernel.create(
            new TableRegistry(), Set.of("ops.summary"), sink);

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void dropsHiddenKeepsVisibleAndRejectsSecondAuthUnmappedOrEmpty() {
        TenantContext current = kernel.tenants().issue(new AuthenticatedMembership(
                Actor.person("user-alpha"), "alpha", "mem-alpha", Set.of("alpha")), "trace-alpha");
        Map<String, ExportFieldRule> policy = Map.of(
                "id", ExportFieldRule.allow(),
                "title", ExportFieldRule.allow(),
                "phone", ExportFieldRule.requireSecondAuthorization(),
                "secret", ExportFieldRule.hidden());

        ExportFieldPolicyGuard.Projection mixed = ExportFieldPolicyGuard.dropHidden(
                current, List.of("id", "title", "secret"), policy, false);
        assertEquals(List.of("id", "title"), mixed.visible());
        assertEquals(List.of("secret"), mixed.dropped());

        ExportFieldPolicyGuard.Projection qualified = ExportFieldPolicyGuard.dropHidden(
                current, List.of("ledger_entry.id", "ledger_entry.secret"), policy, false);
        assertEquals(List.of("ledger_entry.id"), qualified.visible());
        assertEquals(List.of("ledger_entry.secret"), qualified.dropped());

        IsolationException second = assertThrows(IsolationException.class, () -> ExportFieldPolicyGuard.dropHidden(
                current, List.of("id", "phone"), policy, false));
        assertEquals(IsolationErrorCode.FIELD_POLICY_EXPORT, second.error());

        IsolationException unmapped = assertThrows(IsolationException.class, () -> ExportFieldPolicyGuard.dropHidden(
                current, List.of("id", "unknown"), policy, false));
        assertEquals(IsolationErrorCode.FIELD_POLICY_EXPORT, unmapped.error());

        IsolationException emptyLeftover = assertThrows(IsolationException.class, () -> ExportFieldPolicyGuard.dropHidden(
                current, List.of("secret"), policy, false));
        assertEquals(IsolationErrorCode.FIELD_POLICY_EXPORT, emptyLeftover.error());

        IsolationException missingPolicy = assertThrows(IsolationException.class, () -> ExportFieldPolicyGuard.dropHidden(
                current, List.of("id"), Map.of(), false));
        assertEquals(IsolationErrorCode.FIELD_POLICY_EXPORT, missingPolicy.error());

        IsolationException noContext = assertThrows(IsolationException.class, () -> ExportFieldPolicyGuard.dropHidden(
                null, List.of("id"), policy, false));
        assertEquals(IsolationErrorCode.CONTEXT_PROPAGATION_MISSING, noContext.error());

        ExportFieldPolicyGuard.Projection granted = ExportFieldPolicyGuard.dropHidden(
                current, List.of("id", "phone"), policy, true);
        assertEquals(List.of("id", "phone"), granted.visible());
        assertTrue(granted.dropped().isEmpty());
        assertTrue(sink.rejected(IsolationErrorCode.FIELD_POLICY_EXPORT));
        assertTrue(sink.rejectCount() >= 4);
    }
}
