package one.nexus.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.common.audit.AuditCategory;
import one.nexus.common.audit.AuditEvent;
import one.nexus.common.id.UuidV7;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import one.nexus.file.infra.CountingObjectStorage;
import org.junit.jupiter.api.Test;

class AuditColdArchiveJobTest {
    @Test
    void writesTenantAndPlatformPrefixesOnCountingStorageAndRestores() {
        Instant now = Instant.parse("2026-09-22T17:00:00Z");
        var hot = new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        UUID tenant = UuidV7.generate();
        String tenantTrace = "0199a1c0-0000-7000-8000-00000000c091";
        String platformTrace = "0199a1c0-0000-7000-8000-00000000c0pl";
        String youngTrace = "0199a1c0-0000-7000-8000-00000000c010";
        hot.append(agedExport(tenant, tenantTrace, now.minus(AuditRetentionPolicy.HOT_RETENTION).minusSeconds(60)));
        hot.append(agedPlatform(platformTrace, now.minus(AuditRetentionPolicy.HOT_RETENTION).minusSeconds(30)));
        hot.append(agedExport(tenant, youngTrace, now.minusSeconds(3600)));
        var archive = new InMemoryAuditArchivePort(hot);
        archive.copyHotOlderThan(AuditRetentionPolicy.HOT_RETENTION, now);

        CountingObjectStorage counting = new CountingObjectStorage();
        AuditObjectStore store = bind(counting);
        var cold = new AuditColdArchive(hot, archive, store);
        var job = new AuditColdArchiveJob(cold, Clock.fixed(now, ZoneOffset.UTC));

        ColdArchiveRunResult first = job.run();
        assertEquals(2, first.written());
        assertEquals(0, first.skipped());
        assertEquals(2, first.considered());
        assertEquals(2, counting.puts());
        assertEquals(2, first.writtenKeys().size());
        assertTrue(first.writtenKeys().stream().anyMatch(key -> key.startsWith(AuditColdObjectKeys.tenantPrefix(tenant))));
        assertTrue(first.writtenKeys().stream().anyMatch(key -> key.startsWith(AuditColdObjectKeys.platformPrefix())));
        assertFalse(first.writtenKeys().stream().anyMatch(key -> key.contains(youngTrace)));

        List<String> tenantKeys = cold.listTenant(tenant);
        assertEquals(1, tenantKeys.size());
        assertTrue(tenantKeys.getFirst().startsWith("tenants/" + tenant + "/audit/cold/"));
        List<String> platformKeys = cold.listPlatform();
        assertEquals(1, platformKeys.size());
        assertTrue(platformKeys.getFirst().startsWith("platform/audit/cold/"));
        assertTrue(counting.lists() >= 2);

        List<ChainedAuditEvent> restored = cold.restoreByTraceId(tenantTrace);
        assertEquals(1, restored.size());
        assertEquals(AuditCategory.EXPORT, restored.getFirst().event().category());
        assertEquals(tenantTrace, restored.getFirst().event().traceId());
        assertEquals(tenant, restored.getFirst().event().tenantId());
        ChainedAuditEvent byKey = cold.restore(tenantKeys.getFirst());
        assertEquals(restored.getFirst().event().eventId(), byKey.event().eventId());
        assertTrue(hot.verifyChain().intact());
        assertEquals(3, hot.chain().size());

        ColdArchiveRunResult second = job.run();
        assertEquals(0, second.written());
        assertEquals(2, second.skipped());
        assertEquals(2, counting.puts());
        assertEquals(3, hot.chain().size());
    }

    @Test
    void copiesEligibleHotAfterSamePgArchivePurgeWithoutDeletingHot() {
        Instant now = Instant.parse("2026-09-22T17:00:00Z");
        var hot = new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        UUID tenant = UuidV7.generate();
        String expiredTrace = "0199a1c0-0000-7000-8000-00000000c365";
        hot.append(agedExport(tenant, expiredTrace, now.minus(AuditRetentionPolicy.ARCHIVE_RETENTION).minusSeconds(60)));
        var archive = new InMemoryAuditArchivePort(hot);
        new AuditArchiveJob(archive, Clock.fixed(now, ZoneOffset.UTC)).run();
        assertEquals(0, archive.findArchived().size());
        assertEquals(1, hot.chain().size());

        CountingObjectStorage counting = new CountingObjectStorage();
        var cold = new AuditColdArchive(hot, archive, bind(counting));
        ColdArchiveRunResult result = cold.copyEligible(now);
        assertEquals(1, result.written());
        assertEquals(1, cold.restoreByTraceId(expiredTrace).size());
        assertEquals(1, hot.chain().size());
        assertTrue(hot.verifyChain().intact());
        assertEquals(1, counting.puts());
        assertEquals(1, counting.gets());
    }

    private static AuditObjectStore bind(CountingObjectStorage storage) {
        return new ObjectStorageAuditStore(
                "nexus-files", storage::put, storage::get, storage::exists, storage::listKeys);
    }

    private static AuditEvent agedExport(UUID tenant, String trace, Instant occurredAt) {
        return new AuditEvent(
                UuidV7.generate(),
                AuditEvent.VERSION,
                occurredAt,
                AuditCategory.EXPORT,
                AuditCaptures.EXPORT_SUBMITTED,
                tenant,
                tenant,
                tenant,
                false,
                "aged export",
                trace,
                trace,
                null,
                AuditEvent.OUTCOME_ACCEPTED,
                "export",
                "ledger.export.csv",
                Map.of("action", "submit"));
    }

    private static AuditEvent agedPlatform(String trace, Instant occurredAt) {
        return new AuditEvent(
                UuidV7.generate(),
                AuditEvent.VERSION,
                occurredAt,
                AuditCategory.ADMIN,
                "p1.admin.init.accepted",
                null,
                UuidV7.generate(),
                null,
                true,
                "platform admin",
                trace,
                trace,
                null,
                AuditEvent.OUTCOME_ACCEPTED,
                "platform",
                "init",
                Map.of("action", "init"));
    }
}
