package one.nexus.bootstrap.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.AuditPort;
import one.nexus.audit.InMemoryHashChainedAuditPort;
import one.nexus.bootstrap.isolation.AuditPortIsolationSink;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import one.nexus.isolation.api.IsolationAuditEvent;
import one.nexus.isolation.api.IsolationAuditSink;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.p1.domain.AuditEvent;
import org.junit.jupiter.api.Test;

class HashChainedCoverageTest {
    @Test
    void p1AdminAppendMirrorsOntoHashChain() {
        RecordingP1Audit staging = new RecordingP1Audit();
        AuditPort chain = new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        HashChainedP1AuditPort bridge = new HashChainedP1AuditPort(staging, chain);
        UUID tenant = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        String trace = "0199a1c0-0000-7000-8000-00000000c024";
        AuditEvent created = new AuditEvent(
                UUID.fromString("0199a1c0-0000-7000-8000-00000000c025"),
                AuditEvent.TENANT_CREATED,
                AuditEvent.VERSION,
                Instant.parse("2026-09-22T15:00:00Z"),
                tenant,
                tenant,
                tenant,
                true,
                "create",
                trace,
                trace,
                null,
                AuditEvent.OUTCOME_ACCEPTED,
                "tenant",
                tenant.toString(),
                Map.of("code", "g4a"));
        bridge.append(created);
        assertEquals(1, staging.appended.size());
        assertEquals(0, staging.newTx.size());
        assertEquals(AuditEvent.TENANT_CREATED, chain.findByTraceId(trace).getFirst().event().eventType());
        assertTrue(chain.verifyChain().intact());

        AuditEvent rejected = new AuditEvent(
                UUID.fromString("0199a1c0-0000-7000-8000-00000000c026"),
                AuditEvent.TENANT_DISABLE_REJECTED,
                AuditEvent.VERSION,
                Instant.parse("2026-09-22T15:00:01Z"),
                tenant,
                tenant,
                tenant,
                true,
                "protected",
                "0199a1c0-0000-7000-8000-00000000c027",
                "0199a1c0-0000-7000-8000-00000000c027",
                null,
                AuditEvent.OUTCOME_REJECTED,
                "tenant",
                tenant.toString(),
                Map.of("reason_code", "P1.TENANT.0004"));
        bridge.appendNewTransaction(rejected);
        assertEquals(1, staging.newTx.size());
        assertEquals(AuditEvent.TENANT_DISABLE_REJECTED,
                chain.findByTraceId("0199a1c0-0000-7000-8000-00000000c027").getFirst().event().eventType());
    }

    @Test
    void isolationRejectUsesAppendNewTransaction() {
        AuditPort chain = new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        IsolationAuditSink sink = new AuditPortIsolationSink(chain, event -> {});
        String trace = "0199a1c0-0000-7000-8000-00000000c034";
        UUID tenant = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        sink.record(new IsolationAuditEvent(
                UUID.fromString("0199a1c0-0000-7000-8000-00000000c035"),
                Instant.parse("2026-09-22T15:01:00Z"),
                IsolationAuditEvent.ISOLATION_REJECT,
                IsolationErrorCode.MISSING_CONTEXT,
                tenant.toString(),
                tenant.toString(),
                null,
                IsolationErrorCode.MISSING_CONTEXT.defaultMessage(),
                trace,
                Map.of("detail", "membership required")));
        sink.record(new IsolationAuditEvent(
                UUID.fromString("0199a1c0-0000-7000-8000-00000000c036"),
                Instant.parse("2026-09-22T15:01:01Z"),
                IsolationAuditEvent.TENANT_CONTEXT_ISSUED,
                null,
                tenant.toString(),
                tenant.toString(),
                null,
                "issued",
                "0199a1c0-0000-7000-8000-00000000c037",
                Map.of()));
        assertEquals(1, chain.findByTraceId(trace).size());
        assertEquals(AuditCaptures.GUARD_REJECT, chain.findByTraceId(trace).getFirst().event().eventType());
        assertEquals(0, chain.findByTraceId("0199a1c0-0000-7000-8000-00000000c037").size());
        assertTrue(chain.verifyChain().intact());
    }

    private static final class RecordingP1Audit implements one.nexus.p1.port.AuditPort {
        private final List<AuditEvent> appended = new ArrayList<>();
        private final List<AuditEvent> newTx = new ArrayList<>();

        @Override
        public void append(AuditEvent event) {
            appended.add(event);
        }

        @Override
        public void appendNewTransaction(AuditEvent event) {
            newTx.add(event);
        }
    }
}
