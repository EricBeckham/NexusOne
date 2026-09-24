package one.nexus.bootstrap.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.AuditHashChain;
import one.nexus.audit.AuditPort;
import one.nexus.audit.ChainedAuditEvent;
import one.nexus.audit.HashChainVerification;
import one.nexus.audit.InMemoryHashChainedAuditPort;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import one.nexus.p1.domain.DomainException;
import one.nexus.p1.domain.ErrorCode;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.PlatformContext;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AuditChainVerifyControllerTest {
    private static final String SECRET = "chain-must-not-leak";

    @Test
    void intactChainReportsCheckedCountWithoutTheEventPayload() throws Exception {
        CryptoPortFixture fixture = CryptoPortFixture.intact();
        AuditChainVerifyController controller = controller(allow(), fixture.port);
        AuditChainVerifyController.ChainVerification result = controller.verify();

        assertTrue(result.intact());
        assertEquals(1, result.checked());
        assertNull(result.failure());
        assertFalse(new ObjectMapper().writeValueAsString(result).contains(SECRET));
    }

    @Test
    void tamperedChainFailsWithoutReturningThePayload() throws Exception {
        CryptoPortFixture fixture = CryptoPortFixture.tampered();
        AuditChainVerifyController.ChainVerification result = controller(allow(), fixture.port).verify();

        assertFalse(result.intact());
        assertTrue(result.failure().contains("hash mismatch"));
        String json = new ObjectMapper().writeValueAsString(result);
        assertFalse(json.contains(SECRET));
        assertFalse(json.contains("details"));
    }

    @Test
    void missingPlatformDutyDoesNotReadTheChain() {
        AuditPort audits = mock(AuditPort.class);
        IsolationGuard guard = mock(IsolationGuard.class);
        when(guard.requirePlatformContext("audit.chain.verify", PlatformDuty.AUDIT_ADMIN))
                .thenThrow(new DomainException(ErrorCode.DUTY_DENIED, "platform duty required"));
        AuditChainVerifyController controller = new AuditChainVerifyController(guard, audits);

        DomainException denied = assertThrows(DomainException.class, controller::verify);
        assertEquals(ErrorCode.DUTY_DENIED, denied.errorCode());
        verifyNoInteractions(audits);
    }

    private static IsolationGuard allow() {
        IsolationGuard guard = mock(IsolationGuard.class);
        when(guard.requirePlatformContext("audit.chain.verify", PlatformDuty.AUDIT_ADMIN)).thenReturn((PlatformContext) null);
        return guard;
    }

    private static AuditChainVerifyController controller(IsolationGuard guard, AuditPort audits) {
        return new AuditChainVerifyController(guard, audits);
    }

    private static final class CryptoPortFixture {
        private final AuditPort port;

        private CryptoPortFixture(AuditPort port) {
            this.port = port;
        }

        static CryptoPortFixture intact() {
            return new CryptoPortFixture(store(false));
        }

        static CryptoPortFixture tampered() {
            return new CryptoPortFixture(store(true));
        }

        private static AuditPort store(boolean tamper) {
            var crypto = JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL);
            InMemoryHashChainedAuditPort inner = new InMemoryHashChainedAuditPort(crypto);
            inner.append(AuditCaptures.login(
                    true,
                    UUID.fromString("01900000-0000-7000-8000-0000000000a1"),
                    UUID.fromString("01900000-0000-7000-8000-0000000000aa"),
                    "session",
                    "ok",
                    "m22-trace",
                    Map.of("password", SECRET)));
            return new TamperPort(inner, crypto, tamper);
        }
    }

    private static final class TamperPort implements AuditPort {
        private final InMemoryHashChainedAuditPort inner;
        private final one.nexus.crypto.CryptoPort crypto;
        private final boolean tamper;

        private TamperPort(InMemoryHashChainedAuditPort inner, one.nexus.crypto.CryptoPort crypto, boolean tamper) {
            this.inner = inner;
            this.crypto = crypto;
            this.tamper = tamper;
        }

        @Override
        public ChainedAuditEvent append(one.nexus.common.audit.AuditEvent event) {
            return inner.append(event);
        }

        @Override
        public ChainedAuditEvent appendNewTransaction(one.nexus.common.audit.AuditEvent event) {
            return inner.appendNewTransaction(event);
        }

        @Override
        public List<ChainedAuditEvent> findByTraceId(String traceId) {
            return inner.findByTraceId(traceId);
        }

        @Override
        public List<ChainedAuditEvent> findByCorrelationId(String correlationId) {
            return inner.findByCorrelationId(correlationId);
        }

        @Override
        public Optional<ChainedAuditEvent> findByEventId(UUID eventId) {
            return inner.findByEventId(eventId);
        }

        @Override
        public List<ChainedAuditEvent> chain() {
            return inner.chain();
        }

        @Override
        public HashChainVerification verifyChain() {
            List<ChainedAuditEvent> rows = new ArrayList<>(inner.chain());
            if (tamper && !rows.isEmpty()) {
                ChainedAuditEvent first = rows.get(0);
                byte[] hash = first.eventHash();
                hash[0] ^= 1;
                rows.set(0, new ChainedAuditEvent(
                        first.chainIndex(), first.event(), first.previousHash(), hash, first.suite(), first.digestKeyVersion()));
            }
            return new AuditHashChain(crypto).verify(rows);
        }
    }
}
