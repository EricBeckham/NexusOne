package one.nexus.bootstrap.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.AuditHotRetentionJob;
import one.nexus.audit.InMemoryHashChainedAuditPort;
import one.nexus.common.id.UuidV7;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import org.junit.jupiter.api.Test;

class AuditHotRetentionHandlerTest {
    @Test
    void supportsRetentionTaskTypeAndScanDoesNotDropRows() {
        var port = new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        port.append(AuditCaptures.export(
                AuditCaptures.EXPORT_SUBMITTED,
                true,
                UuidV7.generate(),
                UuidV7.generate(),
                "export_task",
                "ledger.export.csv",
                "ok",
                "0199a1c0-0000-7000-8000-000000000091",
                Map.of()));
        AuditHotRetentionJob job = new AuditHotRetentionJob(port);
        AuditHotRetentionHandler handler = new AuditHotRetentionHandler(job);
        assertTrue(handler.supports(AuditHotRetentionJob.TASK_TYPE));
        handler.execute(new IsolatedEnvelope(
                "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                "0199a1c0-0000-7000-8000-000000000091",
                AuditHotRetentionJob.TASK_TYPE,
                "{}"));
        assertEquals(1, port.chain().size());
        assertTrue(port.verifyChain().intact());
        assertTrue(job.scan().archiveDeferred());
    }
}
