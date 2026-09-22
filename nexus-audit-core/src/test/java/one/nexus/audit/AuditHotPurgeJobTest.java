package one.nexus.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import one.nexus.common.audit.AuditCategory;
import one.nexus.common.audit.AuditEvent;
import one.nexus.common.id.UuidV7;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import org.junit.jupiter.api.Test;

class AuditHotPurgeJobTest {
    @Test
    void purgesArchivedPrefixOlderThan90DaysAndLeavesYoungHot() {
        Instant now = Instant.parse("2026-09-22T18:00:00Z");
        var hot = new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        UUID tenant = UuidV7.generate();
        String oldTrace = "0199a1c0-0000-7000-8000-00000000h091";
        String youngTrace = "0199a1c0-0000-7000-8000-00000000h010";
        hot.append(agedExport(tenant, oldTrace, now.minus(AuditRetentionPolicy.HOT_RETENTION).minusSeconds(60)));
        hot.append(agedExport(tenant, youngTrace, now.minusSeconds(3600)));
        var archive = new InMemoryAuditArchivePort(hot);
        archive.copyHotOlderThan(AuditRetentionPolicy.HOT_RETENTION, now);
        var job = new AuditHotPurgeJob(
                AuditHotPurge.inMemory(hot, archive, new InMemoryAuditObjectStore()),
                Clock.fixed(now, ZoneOffset.UTC));

        HotPurgeRunResult first = job.run();
        assertEquals(1, first.purged());
        assertEquals(1, first.remainingHot());
        assertEquals(0, first.skippedUncopied());
        assertEquals(now.minus(AuditRetentionPolicy.HOT_RETENTION), first.cutoff());
        assertEquals(0, hot.findByTraceId(oldTrace).size());
        assertEquals(1, hot.findByTraceId(youngTrace).size());
        assertEquals(1, archive.findArchivedByTraceId(oldTrace).size());
        assertTrue(hot.verifyChain().intact());
        assertEquals(1, hot.verifyChain().checked());

        HotPurgeRunResult second = job.run();
        assertEquals(0, second.purged());
        assertEquals(1, second.remainingHot());
        assertEquals(1, hot.chain().size());
        assertTrue(hot.verifyChain().intact());
    }

    @Test
    void leavesUncopiedAgedRowAndYoungPrefixBlocksLaterAged() {
        Instant now = Instant.parse("2026-09-22T18:00:00Z");
        var hot = new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        UUID tenant = UuidV7.generate();
        String uncopied = "0199a1c0-0000-7000-8000-00000000h0nc";
        String laterAged = "0199a1c0-0000-7000-8000-00000000h0la";
        hot.append(agedExport(tenant, uncopied, now.minus(AuditRetentionPolicy.HOT_RETENTION).minusSeconds(90)));
        hot.append(agedExport(tenant, laterAged, now.minus(AuditRetentionPolicy.HOT_RETENTION).minusSeconds(30)));
        var archive = new InMemoryAuditArchivePort(hot);
        var job = new AuditHotPurgeJob(
                AuditHotPurge.inMemory(hot, archive, new InMemoryAuditObjectStore()),
                Clock.fixed(now, ZoneOffset.UTC));

        HotPurgeRunResult result = job.run();
        assertEquals(0, result.purged());
        assertEquals(2, result.remainingHot());
        assertEquals(2, result.skippedUncopied());
        assertEquals(2, hot.chain().size());
        assertTrue(hot.verifyChain().intact());
    }

    @Test
    void keeps366DayArchiveOnlyRowUntilColdCopyExists() {
        Instant now = Instant.parse("2026-09-22T18:00:00Z");
        var hot = new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        UUID tenant = UuidV7.generate();
        String expired = "0199a1c0-0000-7000-8000-00000000h365";
        String kept = "0199a1c0-0000-7000-8000-00000000h200";
        hot.append(agedExport(
                tenant, expired, now.minus(AuditRetentionPolicy.ARCHIVE_RETENTION).minusSeconds(60)));
        hot.append(agedExport(
                tenant, kept, now.minus(AuditRetentionPolicy.ARCHIVE_RETENTION).plusSeconds(86_400)));
        var archive = new InMemoryAuditArchivePort(hot);
        archive.copyHotOlderThan(AuditRetentionPolicy.HOT_RETENTION, now);
        var store = new InMemoryAuditObjectStore();
        var job = new AuditHotPurgeJob(
                AuditHotPurge.inMemory(hot, archive, store), Clock.fixed(now, ZoneOffset.UTC));

        HotPurgeRunResult blocked = job.run();
        assertEquals(0, blocked.purged());
        assertEquals(2, blocked.remainingHot());
        assertEquals(2, blocked.skippedUncopied());
        assertEquals(2, hot.chain().size());

        new AuditColdArchive(hot, archive, store).copyEligible(now);
        HotPurgeRunResult afterCold = job.run();
        assertEquals(2, afterCold.purged());
        assertEquals(0, afterCold.remainingHot());
        assertEquals(0, afterCold.skippedUncopied());
        assertEquals(0, hot.chain().size());
        assertTrue(hot.verifyChain().intact());
        assertEquals(1, archive.findArchivedByTraceId(expired).size());
        assertEquals(1, archive.findArchivedByTraceId(kept).size());
        assertEquals(1, new AuditColdArchive(hot, archive, store).restoreByTraceId(expired).size());
    }

    @Test
    void appendAfterPurgeKeepsSuffixAndHead() {
        Instant now = Instant.parse("2026-09-22T18:00:00Z");
        var hot = new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        UUID tenant = UuidV7.generate();
        String oldTrace = "0199a1c0-0000-7000-8000-00000000h0ap";
        String youngTrace = "0199a1c0-0000-7000-8000-00000000h0yn";
        String nextTrace = "0199a1c0-0000-7000-8000-00000000h0nx";
        hot.append(agedExport(tenant, oldTrace, now.minus(AuditRetentionPolicy.HOT_RETENTION).minusSeconds(60)));
        hot.append(agedExport(tenant, youngTrace, now.minusSeconds(120)));
        var archive = new InMemoryAuditArchivePort(hot);
        archive.copyHotOlderThan(AuditRetentionPolicy.HOT_RETENTION, now);
        new AuditHotPurgeJob(
                        AuditHotPurge.inMemory(hot, archive, new InMemoryAuditObjectStore()),
                        Clock.fixed(now, ZoneOffset.UTC))
                .run();
        assertEquals(1, hot.chain().getFirst().chainIndex());

        hot.append(agedExport(tenant, nextTrace, now));
        assertEquals(2, hot.chain().size());
        assertEquals(2, hot.chain().getLast().chainIndex());
        assertTrue(hot.verifyChain().intact());
        assertEquals(1, hot.findByTraceId(youngTrace).size());
        assertEquals(1, hot.findByTraceId(nextTrace).size());
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
}
