package one.nexus.bootstrap.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import one.nexus.audit.AuditColdArchive;
import one.nexus.audit.AuditColdArchiveJob;
import one.nexus.audit.AuditColdObjectKeys;
import one.nexus.audit.AuditRetentionPolicy;
import one.nexus.audit.ColdArchiveRunResult;
import one.nexus.audit.InMemoryAuditArchivePort;
import one.nexus.audit.InMemoryHashChainedAuditPort;
import one.nexus.audit.ObjectStorageAuditStore;
import one.nexus.common.audit.AuditCategory;
import one.nexus.common.audit.AuditEvent;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.common.id.UuidV7;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import one.nexus.event.isolation.ExecutionIsolation;
import one.nexus.file.infra.CountingObjectStorage;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class G4AuditColdArchiveJobTest {
    private static final Instant NOW = Instant.parse("2026-09-22T17:00:00Z");

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void platformRunnerWritesCountingStorageAndRestores() {
        var hot = new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        UUID tenant = UuidV7.generate();
        String tenantTrace = "0199a1c0-0000-7000-8000-00000000b091";
        String platformTrace = "0199a1c0-0000-7000-8000-00000000b0pl";
        hot.append(aged(tenant, tenantTrace, NOW.minus(AuditRetentionPolicy.HOT_RETENTION).minusSeconds(30), false));
        hot.append(aged(null, platformTrace, NOW.minus(AuditRetentionPolicy.HOT_RETENTION).minusSeconds(15), true));
        var archive = new InMemoryAuditArchivePort(hot);
        archive.copyHotOlderThan(AuditRetentionPolicy.HOT_RETENTION, NOW);
        CountingObjectStorage counting = new CountingObjectStorage();
        var cold = new AuditColdArchive(
                hot,
                archive,
                new ObjectStorageAuditStore(
                        "nexus-files", counting::put, counting::get, counting::exists, counting::listKeys));
        var job = new AuditColdArchiveJob(cold, Clock.fixed(NOW, ZoneOffset.UTC));
        var handler = new AuditColdArchiveTaskHandler(job);
        IsolationKernel kernel = IsolationKernel.create(
                new TableRegistry(), Set.of(AuditColdArchiveJob.PLATFORM_PATH), new CollectingAuditSink());
        var runner = new AuditColdArchiveRunner(new ExecutionIsolation(kernel), handler, job);

        ColdArchiveRunResult result = runner.runOnce("trace-cold-g4");
        assertEquals(2, result.written());
        assertEquals(2, counting.puts());
        assertTrue(result.writtenKeys().stream().anyMatch(key -> key.startsWith(AuditColdObjectKeys.tenantPrefix(tenant))));
        assertTrue(result.writtenKeys().stream().anyMatch(key -> key.startsWith(AuditColdObjectKeys.platformPrefix())));
        assertEquals(1, cold.listTenant(tenant).size());
        assertEquals(1, cold.restoreByTraceId(tenantTrace).size());
        assertEquals(AuditCategory.EXPORT, cold.restoreByTraceId(platformTrace).getFirst().event().category());
        assertEquals(2, hot.chain().size());
        assertTrue(hot.verifyChain().intact());
        assertTrue(handler.supports(AuditColdArchiveJob.TASK_TYPE));
    }

    @Test
    void tenantContextCannotRunColdArchiveHandler() {
        var hot = new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        CountingObjectStorage counting = new CountingObjectStorage();
        var job = new AuditColdArchiveJob(
                new AuditColdArchive(
                        hot,
                        new InMemoryAuditArchivePort(hot),
                        new ObjectStorageAuditStore(
                                "nexus-files", counting::put, counting::get, counting::exists, counting::listKeys)),
                Clock.fixed(NOW, ZoneOffset.UTC));
        var handler = new AuditColdArchiveTaskHandler(job);
        IsolationKernel kernel = IsolationKernel.create(
                new TableRegistry(), Set.of(AuditColdArchiveJob.PLATFORM_PATH), new CollectingAuditSink());
        AccessContextHolder.set(kernel.tenants().issue(
                new AuthenticatedMembership(Actor.person("user-a"), "alpha", "mem-a", Set.of("alpha")),
                "trace-tenant"));
        NexusException ex = assertThrows(
                NexusException.class,
                () -> handler.execute(IsolatedEnvelope.fromCurrent(AuditColdArchiveJob.TASK_TYPE, "{}")));
        assertEquals(ErrorCode.CTX_INVALID_PLATFORM, ex.errorCode());
        assertEquals(0, counting.puts());
        assertEquals(0, job.lastResult() == null ? 0 : job.lastResult().written());
    }

    private static AuditEvent aged(UUID tenant, String trace, Instant occurredAt, boolean platform) {
        return new AuditEvent(
                UuidV7.generate(),
                AuditEvent.VERSION,
                occurredAt,
                AuditCategory.EXPORT,
                "p3.export.submitted",
                tenant,
                tenant == null ? UuidV7.generate() : tenant,
                tenant,
                platform,
                "aged",
                trace,
                trace,
                null,
                AuditEvent.OUTCOME_ACCEPTED,
                "export",
                "ledger.export.csv",
                Map.of());
    }
}
