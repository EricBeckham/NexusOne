package one.nexus.bootstrap.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.AuditHotRetentionJob;
import one.nexus.audit.AuditRetentionScan;
import one.nexus.audit.AuditTrace;
import one.nexus.audit.InMemoryHashChainedAuditPort;
import one.nexus.bootstrap.isolation.AuditPortIsolationSink;
import one.nexus.common.audit.AuditCategory;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.api.IsolationAuditSink;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.boundary.IsolatedExport;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * G4 follow-up: accepted export operations append to the existing hash chain
 * and are findable by trace. Cross-tenant still uses guard-reject
 * {@code appendNewTransaction}. Retention Job is a dry-run stub.
 */
class G4ExportAuditIT {
    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID ACTOR = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final String TRACE = "0199a1c0-0000-7000-8000-0000000000e4";

    private final CollectingAuditSink collecting = new CollectingAuditSink();
    private final InMemoryHashChainedAuditPort port =
            new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
    private final IsolationKernel kernel = IsolationKernel.create(
            new TableRegistry(),
            Set.of("files.reconcile", "cache.ops", "exec.quartz"),
            IsolationAuditSink.tee(collecting, new AuditPortIsolationSink(port, event -> {})));
    private final AuditedExport exports = new AuditedExport(port);

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void acceptedExportLandsOnChainAndIsQueryableByTrace() {
        bind(TENANT.toString(), ACTOR.toString(), TRACE);
        IsolatedEnvelope envelope = exports.submit(TENANT.toString(), "{\"ledger_code\":\"leave\"}");
        assertEquals(IsolatedExport.TASK_TYPE, envelope.payloadType());
        String key = exports.writeFile(TENANT.toString(), "export/leave.csv", "leave\n".getBytes());
        AtomicBoolean ran = new AtomicBoolean(false);
        exports.dispatch(envelope, TENANT.toString(), item -> ran.set(true));
        assertTrue(ran.get());
        assertEquals("tenants/" + TENANT + "/export/leave.csv", key);

        var rows = port.findByTraceId(TRACE);
        assertEquals(3, rows.size(), rows.toString());
        assertTrue(rows.stream().allMatch(event -> event.event().category() == AuditCategory.DATA));
        assertEquals(AuditCaptures.EXPORT_SUBMITTED, rows.get(0).event().eventType());
        assertEquals(AuditCaptures.EXPORT_FILE_WRITTEN, rows.get(1).event().eventType());
        assertEquals(AuditCaptures.EXPORT_DISPATCHED, rows.get(2).event().eventType());
        assertTrue(rows.stream().noneMatch(event ->
                event.event().details().values().stream().anyMatch(value -> value.contains("super-secret"))));
        assertTrue(port.verifyChain().intact());

        var json = AuditTrace.jsonForTenant(TRACE, TENANT, rows);
        @SuppressWarnings("unchecked")
        var events = (java.util.List<java.util.Map<String, Object>>) json.get("events");
        assertEquals(3, events.size());
        assertEquals(AuditCaptures.EXPORT_SUBMITTED, events.getFirst().get("event_type"));

        AuditHotRetentionJob job = new AuditHotRetentionJob(port);
        AuditRetentionScan scan = job.scan();
        assertEquals(3, scan.hotCount());
        assertTrue(scan.archiveDeferred());
        assertEquals(3, port.chain().size());
    }

    @Test
    void crossTenantExportDoesNotAppendAcceptedExportEvent() {
        bind(TENANT.toString(), ACTOR.toString(), TRACE);
        AtomicBoolean file = new AtomicBoolean(false);
        IsolationException ex = assertThrows(IsolationException.class,
                () -> exports.submit("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", "{}"));
        assertEquals(IsolationErrorCode.CROSS_TENANT_EXPORT, ex.error());
        IsolationException fileEx = assertThrows(IsolationException.class,
                () -> exports.writeFile("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", "export/x.csv", new byte[] {1}));
        assertEquals(IsolationErrorCode.CROSS_TENANT_EXPORT, fileEx.error());
        assertFalse(file.get());
        assertTrue(collecting.rejected(IsolationErrorCode.CROSS_TENANT_EXPORT));
        assertTrue(port.findByTraceId(TRACE).stream().noneMatch(event ->
                AuditCaptures.EXPORT_SUBMITTED.equals(event.event().eventType())
                        || AuditCaptures.EXPORT_FILE_WRITTEN.equals(event.event().eventType())));
        assertTrue(port.findByTraceId(TRACE).stream().anyMatch(event ->
                event.event().category() == AuditCategory.GUARD_REJECT
                        && "P1.M02.019".equals(event.event().resourceId())));
        assertTrue(port.verifyChain().intact());
    }

    private void bind(String tenantId, String actorId, String traceId) {
        AccessContextHolder.set(kernel.tenants().issue(
                new AuthenticatedMembership(
                        Actor.person(actorId), tenantId, "mem-" + tenantId, Set.of(tenantId)),
                traceId));
    }
}
