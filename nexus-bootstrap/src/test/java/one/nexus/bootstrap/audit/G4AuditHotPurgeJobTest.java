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
import one.nexus.audit.AuditHotPurge;
import one.nexus.audit.AuditHotPurgeJob;
import one.nexus.audit.AuditRetentionPolicy;
import one.nexus.audit.HotPurgeRunResult;
import one.nexus.audit.InMemoryAuditArchivePort;
import one.nexus.audit.InMemoryAuditObjectStore;
import one.nexus.audit.InMemoryHashChainedAuditPort;
import one.nexus.common.audit.AuditCategory;
import one.nexus.common.audit.AuditEvent;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.common.id.UuidV7;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import one.nexus.event.isolation.ExecutionIsolation;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class G4AuditHotPurgeJobTest {
    private static final Instant NOW = Instant.parse("2026-09-22T18:00:00Z");

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void platformRunnerPurgesCopiedPrefixThroughTaskHandler() {
        var hot = new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        UUID tenant = UuidV7.generate();
        String oldTrace = "0199a1c0-0000-7000-8000-00000000r091";
        String youngTrace = "0199a1c0-0000-7000-8000-00000000r010";
        hot.append(aged(tenant, oldTrace, NOW.minus(AuditRetentionPolicy.HOT_RETENTION).minusSeconds(30)));
        hot.append(aged(tenant, youngTrace, NOW.minusSeconds(60)));
        var archive = new InMemoryAuditArchivePort(hot);
        archive.copyHotOlderThan(AuditRetentionPolicy.HOT_RETENTION, NOW);
        var job = new AuditHotPurgeJob(
                AuditHotPurge.inMemory(hot, archive, new InMemoryAuditObjectStore()),
                Clock.fixed(NOW, ZoneOffset.UTC));
        var handler = new AuditHotPurgeTaskHandler(job);
        IsolationKernel kernel = IsolationKernel.create(
                new TableRegistry(), Set.of(AuditHotPurgeJob.PLATFORM_PATH), new CollectingAuditSink());
        var runner = new AuditHotPurgeRunner(new ExecutionIsolation(kernel), handler, job);

        HotPurgeRunResult result = runner.runOnce("trace-hot-purge-g4");
        assertEquals(1, result.purged());
        assertEquals(1, result.remainingHot());
        assertEquals(0, hot.findByTraceId(oldTrace).size());
        assertEquals(1, hot.findByTraceId(youngTrace).size());
        assertEquals(1, archive.findArchivedByTraceId(oldTrace).size());
        assertTrue(hot.verifyChain().intact());
        assertTrue(handler.supports(AuditHotPurgeJob.TASK_TYPE));
    }

    @Test
    void tenantContextCannotRunHotPurgeHandler() {
        var hot = new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        var job = new AuditHotPurgeJob(
                AuditHotPurge.inMemory(hot, new InMemoryAuditArchivePort(hot), new InMemoryAuditObjectStore()),
                Clock.fixed(NOW, ZoneOffset.UTC));
        var handler = new AuditHotPurgeTaskHandler(job);
        IsolationKernel kernel = IsolationKernel.create(
                new TableRegistry(), Set.of(AuditHotPurgeJob.PLATFORM_PATH), new CollectingAuditSink());
        AccessContextHolder.set(kernel.tenants().issue(
                new AuthenticatedMembership(Actor.person("user-a"), "alpha", "mem-a", Set.of("alpha")),
                "trace-tenant"));
        NexusException ex = assertThrows(
                NexusException.class,
                () -> handler.execute(IsolatedEnvelope.fromCurrent(AuditHotPurgeJob.TASK_TYPE, "{}")));
        assertEquals(ErrorCode.CTX_INVALID_PLATFORM, ex.errorCode());
        assertEquals(0, job.lastResult() == null ? 0 : job.lastResult().purged());
        assertEquals(0, hot.chain().size());
    }

    private static AuditEvent aged(UUID tenant, String trace, Instant occurredAt) {
        return new AuditEvent(
                UuidV7.generate(),
                AuditEvent.VERSION,
                occurredAt,
                AuditCategory.EXPORT,
                "p3.export.submitted",
                tenant,
                tenant,
                tenant,
                false,
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
