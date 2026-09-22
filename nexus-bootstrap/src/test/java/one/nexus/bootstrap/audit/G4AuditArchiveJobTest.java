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
import one.nexus.audit.ArchiveRunResult;
import one.nexus.audit.AuditArchiveJob;
import one.nexus.audit.AuditRetentionPolicy;
import one.nexus.audit.InMemoryAuditArchivePort;
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

class G4AuditArchiveJobTest {
    private static final Instant NOW = Instant.parse("2026-09-22T15:00:00Z");

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void platformRunnerCopiesAgedHotRowsThroughTaskHandler() {
        var hot = new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        UUID tenant = UuidV7.generate();
        String oldTrace = "0199a1c0-0000-7000-8000-00000000r091";
        hot.append(aged(tenant, oldTrace, NOW.minus(AuditRetentionPolicy.HOT_RETENTION).minusSeconds(30)));
        var archive = new InMemoryAuditArchivePort(hot);
        var job = new AuditArchiveJob(archive, Clock.fixed(NOW, ZoneOffset.UTC));
        var handler = new AuditArchiveTaskHandler(job);
        IsolationKernel kernel = IsolationKernel.create(
                new TableRegistry(), Set.of(AuditArchiveJob.PLATFORM_PATH), new CollectingAuditSink());
        var runner = new AuditArchiveRunner(new ExecutionIsolation(kernel), handler, job);

        ArchiveRunResult result = runner.runOnce("trace-archive-g4");
        assertEquals(1, result.copied());
        assertEquals(0, result.purged());
        assertEquals(1, archive.findArchivedByTraceId(oldTrace).size());
        assertEquals(1, hot.chain().size());
        assertTrue(hot.verifyChain().intact());
        assertTrue(handler.supports(AuditArchiveJob.TASK_TYPE));
        assertTrue(result.purgeImplemented());
    }

    @Test
    void platformRunnerPurgesArchiveOlderThan365Days() {
        var hot = new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        UUID tenant = UuidV7.generate();
        String expiredTrace = "0199a1c0-0000-7000-8000-00000000r365";
        String keptTrace = "0199a1c0-0000-7000-8000-00000000r200";
        hot.append(aged(tenant, expiredTrace, NOW.minus(AuditRetentionPolicy.ARCHIVE_RETENTION).minusSeconds(30)));
        hot.append(aged(tenant, keptTrace, NOW.minus(AuditRetentionPolicy.ARCHIVE_RETENTION).plusSeconds(86_400)));
        var archive = new InMemoryAuditArchivePort(hot);
        var job = new AuditArchiveJob(archive, Clock.fixed(NOW, ZoneOffset.UTC));
        var handler = new AuditArchiveTaskHandler(job);
        IsolationKernel kernel = IsolationKernel.create(
                new TableRegistry(), Set.of(AuditArchiveJob.PLATFORM_PATH), new CollectingAuditSink());
        var runner = new AuditArchiveRunner(new ExecutionIsolation(kernel), handler, job);

        ArchiveRunResult result = runner.runOnce("trace-archive-expiry-g4");
        assertEquals(2, result.copied());
        assertEquals(1, result.purged());
        assertEquals(1, result.archiveSize());
        assertTrue(result.purgeImplemented());
        assertEquals(0, archive.findArchivedByTraceId(expiredTrace).size());
        assertEquals(1, archive.findArchivedByTraceId(keptTrace).size());
        assertEquals(2, hot.chain().size());
        assertTrue(hot.verifyChain().intact());
    }

    @Test
    void tenantContextCannotRunArchiveHandler() {
        var hot = new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        var job = new AuditArchiveJob(new InMemoryAuditArchivePort(hot), Clock.fixed(NOW, ZoneOffset.UTC));
        var handler = new AuditArchiveTaskHandler(job);
        IsolationKernel kernel = IsolationKernel.create(
                new TableRegistry(), Set.of(AuditArchiveJob.PLATFORM_PATH), new CollectingAuditSink());
        AccessContextHolder.set(kernel.tenants().issue(
                new AuthenticatedMembership(Actor.person("user-a"), "alpha", "mem-a", Set.of("alpha")),
                "trace-tenant"));
        NexusException ex = assertThrows(
                NexusException.class,
                () -> handler.execute(IsolatedEnvelope.fromCurrent(AuditArchiveJob.TASK_TYPE, "{}")));
        assertEquals(ErrorCode.CTX_INVALID_PLATFORM, ex.errorCode());
        assertEquals(0, job.lastResult() == null ? 0 : job.lastResult().copied());
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
