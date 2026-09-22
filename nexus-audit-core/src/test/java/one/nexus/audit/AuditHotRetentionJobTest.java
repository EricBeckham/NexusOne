package one.nexus.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import one.nexus.common.id.UuidV7;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import org.junit.jupiter.api.Test;

class AuditHotRetentionJobTest {
    @Test
    void scanClassifiesHotVersusPastHotAndNeverDropsRows() {
        var port = new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        UUID tenant = UuidV7.generate();
        String trace = "0199a1c0-0000-7000-8000-000000000090";
        port.append(AuditCaptures.export(
                AuditCaptures.EXPORT_SUBMITTED,
                true,
                tenant,
                tenant,
                "export_task",
                "ledger.export.csv",
                "ok",
                trace,
                Map.of()));
        Instant now = Instant.parse("2026-09-22T15:00:00Z");
        AuditHotRetentionJob job = new AuditHotRetentionJob(port);
        AuditRetentionScan hot = job.scan(now);
        assertEquals(1, hot.hotCount());
        assertEquals(0, hot.pastHotCount());
        assertEquals(1, hot.totalCount());
        assertTrue(hot.archiveDeferred());
        assertFalse(hot.wouldArchive());
        assertEquals(AuditRetentionPolicy.HOT_DAYS, 90);
        assertEquals(AuditRetentionPolicy.ARCHIVE_DAYS, 365);
        assertFalse(AuditRetentionPolicy.ARCHIVE_IMPLEMENTED);

        AuditRetentionScan aged = job.scan(now.plus(Duration.ofDays(91)));
        assertEquals(0, aged.hotCount());
        assertEquals(1, aged.pastHotCount());
        assertEquals(1, aged.totalCount());
        assertTrue(aged.wouldArchive());
        assertTrue(aged.archiveDeferred());
        assertEquals(1, port.chain().size());
        assertTrue(port.verifyChain().intact());
        assertEquals(1, port.findByTraceId(trace).size());
    }

    @Test
    void policyCutoffIsNinetyDays() {
        Instant now = Instant.parse("2026-09-22T00:00:00Z");
        Instant cutoff = AuditRetentionPolicy.hotCutoff(now);
        assertEquals(Instant.parse("2026-06-24T00:00:00Z"), cutoff);
        assertTrue(AuditRetentionPolicy.isHot(cutoff, now));
        assertFalse(AuditRetentionPolicy.isHot(cutoff.minusSeconds(1), now));
    }
}
