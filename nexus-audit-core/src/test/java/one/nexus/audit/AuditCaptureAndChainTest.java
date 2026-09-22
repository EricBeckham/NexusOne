package one.nexus.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.common.audit.AuditCategory;
import one.nexus.common.audit.AuditEvent;
import one.nexus.common.id.UuidV7;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import org.junit.jupiter.api.Test;

class AuditCaptureAndChainTest {
    @Test
    void redactsSecretsBeforeHashing() {
        AuditEvent event = AuditCaptures.login(
                true,
                UuidV7.generate(),
                UuidV7.generate(),
                "sess-1",
                "password-login",
                "0199a1c0-0000-7000-8000-000000000042",
                Map.of("password", "super-secret", "idp", "stub"));
        assertEquals(SensitiveRedactor.REDACTED, event.details().get("password"));
        assertEquals("stub", event.details().get("idp"));
        assertFalse(CanonicalAudit.detailsJson(event.details()).contains("super-secret"));
    }

    @Test
    void sameTraceCorrelatesLoginPermissionAdminDataGuardReject() {
        var port = new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        UUID tenant = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        UUID actor = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        String trace = "0199a1c0-0000-7000-8000-000000000099";
        port.append(AuditCaptures.login(true, tenant, actor, "sess", "ok", trace, Map.of()));
        port.append(AuditCaptures.permission(false, tenant, actor, "ledger", "row-1", "deny", trace, Map.of("action", "write")));
        port.append(AuditCaptures.admin("p1.tenant.enabled", true, tenant, actor, tenant, "tenant", tenant.toString(), "enable", trace, Map.of()));
        port.append(AuditCaptures.data("p3.data.updated", true, tenant, actor, "ledger", "row-1", "update", trace, Map.of("field", "name")));
        port.appendNewTransaction(AuditCaptures.guardReject(new GuardRejectCapture(
                UuidV7.generate(),
                Instant.parse("2026-09-22T00:00:00Z"),
                "P1.M02.002",
                actor.toString(),
                tenant.toString(),
                null,
                "cross-tenant write rejected",
                trace,
                Map.of("detail", "tenant mismatch"))));
        List<ChainedAuditEvent> correlated = AuditTrace.correlate(port, trace);
        assertEquals(5, correlated.size());
        assertEquals(
                List.of(
                        AuditCategory.LOGIN,
                        AuditCategory.PERMISSION,
                        AuditCategory.ADMIN,
                        AuditCategory.DATA,
                        AuditCategory.GUARD_REJECT),
                AuditTrace.categories(correlated));
        HashChainVerification verification = port.verifyChain();
        assertTrue(verification.intact(), verification.failure());
        assertEquals(5, verification.checked());
        Map<String, String> log = AuditTrace.logFields(correlated.getFirst());
        assertEquals(trace, log.get("trace_id"));
        assertEquals(trace, log.get("traceId"));
        assertFalse(log.containsKey("password"));
        assertEquals("LOGIN", log.get("audit_category"));
        UUID other = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
        Map<String, Object> json = AuditTrace.jsonForTenant(trace, tenant, correlated);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mine = (List<Map<String, Object>>) json.get("events");
        assertEquals(5, mine.size());
        assertEquals("p2.auth.login.accepted", mine.getFirst().get("event_type"));
        assertTrue(String.valueOf(mine.getFirst().get("event_hash")).matches("[0-9a-f]+"));
        Map<String, Object> empty = AuditTrace.jsonForTenant(trace, other, correlated);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> leaked = (List<Map<String, Object>>) empty.get("events");
        assertEquals(0, leaked.size());
    }

    @Test
    void exportActionsShareTraceOnExistingHashChain() {
        var port = new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        UUID tenant = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        UUID actor = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        String trace = "0199a1c0-0000-7000-8000-0000000000e4";
        port.append(AuditCaptures.data(
                "p3.data.changed", true, tenant, actor, "ledger", "row-1", "update", trace, Map.of()));
        port.append(AuditCaptures.export(
                AuditCaptures.EXPORT_SUBMITTED,
                true,
                tenant,
                actor,
                "export",
                "ledger.export.csv",
                "export submit",
                trace,
                Map.of("action", "submit", "password", "nope")));
        port.append(AuditCaptures.export(
                AuditCaptures.EXPORT_FILE_WRITTEN,
                true,
                tenant,
                actor,
                "export",
                "export/2026/note.csv",
                "export writeFile",
                trace,
                Map.of("action", "writeFile")));
        List<ChainedAuditEvent> correlated = AuditTrace.correlate(port, trace);
        assertEquals(3, correlated.size());
        assertEquals(
                List.of(AuditCategory.DATA, AuditCategory.EXPORT, AuditCategory.EXPORT),
                AuditTrace.categories(correlated));
        assertEquals(AuditCaptures.EXPORT_SUBMITTED, correlated.get(1).event().eventType());
        assertEquals(SensitiveRedactor.REDACTED, correlated.get(1).event().details().get("password"));
        HashChainVerification verification = port.verifyChain();
        assertTrue(verification.intact(), verification.failure());
        Map<String, Object> json = AuditTrace.jsonForTenant(trace, tenant, correlated);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mine = (List<Map<String, Object>>) json.get("events");
        assertEquals(3, mine.size());
        assertEquals("p3.export.submitted", mine.get(1).get("event_type"));
    }

    @Test
    void exportWizardBeginCompleteShareTraceOnExistingHashChain() {
        var port = new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        UUID tenant = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        UUID actor = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        String trace = "0199a1c0-0000-7000-8000-00000000c254";
        port.append(AuditCaptures.export(
                AuditCaptures.EXPORT_BEGUN,
                true,
                tenant,
                actor,
                IsolatedExportTask.TYPE,
                "wizard-1",
                "wizard.begin",
                trace,
                Map.of("step", "begin", "surface", "export.wizard")));
        port.append(AuditCaptures.export(
                AuditCaptures.EXPORT_SUBMITTED,
                true,
                tenant,
                actor,
                IsolatedExportTask.TYPE,
                IsolatedExportTask.TYPE,
                "export submit",
                trace,
                Map.of("action", "submit")));
        port.append(AuditCaptures.export(
                AuditCaptures.EXPORT_COMPLETED,
                true,
                tenant,
                actor,
                IsolatedExportTask.TYPE,
                "wizard-1",
                "wizard.complete",
                trace,
                Map.of("step", "complete", "surface", "export.wizard")));
        List<ChainedAuditEvent> correlated = AuditTrace.correlate(port, trace);
        assertEquals(3, correlated.size());
        assertEquals(AuditCaptures.EXPORT_BEGUN, correlated.get(0).event().eventType());
        assertEquals(AuditCaptures.EXPORT_COMPLETED, correlated.get(2).event().eventType());
        assertTrue(correlated.stream().allMatch(event -> event.event().category() == AuditCategory.EXPORT));
        assertTrue(port.verifyChain().intact(), port.verifyChain().failure());
    }

    private static final class IsolatedExportTask {
        static final String TYPE = "ledger.export.csv";
    }

    @Test
    void gmDigestChainDetectsTamper() {
        var crypto = JcaCryptoPort.ephemeral(CryptoSuite.GM);
        var port = new InMemoryHashChainedAuditPort(crypto);
        UUID tenant = UuidV7.generate();
        String trace = "0af7651916cd43dd8448eb211c80319c";
        port.append(AuditCaptures.approval("p3.approval.approved", true, tenant, tenant, "task-1", "ok", trace, Map.of()));
        port.append(AuditCaptures.approval("p3.approval.withdrawn", true, tenant, tenant, "task-1", "withdraw", trace, Map.of()));
        assertTrue(port.verifyChain().intact());
        UUID first = port.chain().getFirst().event().eventId();
        HashChainVerification broken = new AuditHashChain(crypto).verify(port.copyWithTamperedHash(first));
        assertFalse(broken.intact());
        assertTrue(broken.failure().contains("event hash mismatch"));
    }
}
