package one.nexus.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

class AuditArchiveJobTest {
    @Test
    void copiesOlderThan90DaysAndLeavesHotChainUntouched() {
        Instant now = Instant.parse("2026-09-22T15:00:00Z");
        var port = new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        UUID tenant = UuidV7.generate();
        String oldTrace = "0199a1c0-0000-7000-8000-00000000a091";
        String hotTrace = "0199a1c0-0000-7000-8000-00000000a010";
        port.append(agedExport(tenant, oldTrace, now.minus(AuditRetentionPolicy.HOT_RETENTION).minusSeconds(60)));
        port.append(agedExport(tenant, hotTrace, now.minus(AuditRetentionPolicy.HOT_RETENTION).plusSeconds(3600)));
        var archive = new InMemoryAuditArchivePort(port);
        var job = new AuditArchiveJob(archive, Clock.fixed(now, ZoneOffset.UTC));

        ArchiveRunResult first = job.run();
        assertEquals(1, first.copied());
        assertEquals(1, first.archiveSize());
        assertFalse(first.purgeImplemented());
        assertEquals(now.minus(AuditRetentionPolicy.HOT_RETENTION), first.cutoff());
        assertEquals(1, archive.findArchivedByTraceId(oldTrace).size());
        assertEquals(0, archive.findArchivedByTraceId(hotTrace).size());
        assertEquals(2, port.chain().size());
        assertTrue(port.verifyChain().intact());
        assertEquals(AuditCategory.EXPORT, archive.findArchivedByTraceId(oldTrace).getFirst().event().category());

        ArchiveRunResult second = job.run();
        assertEquals(0, second.copied());
        assertEquals(1, second.archiveSize());
        assertEquals(2, port.chain().size());

        ArchiveRunResult purge = archive.purgeExpiredArchive(AuditRetentionPolicy.ARCHIVE_RETENTION, now);
        assertFalse(purge.purgeImplemented());
        assertEquals(1, archive.archived().size());
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
