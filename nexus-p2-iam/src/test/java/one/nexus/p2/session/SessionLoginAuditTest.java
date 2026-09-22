package one.nexus.p2.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.AuditPort;
import one.nexus.audit.ChainedAuditEvent;
import one.nexus.audit.InMemoryHashChainedAuditPort;
import one.nexus.common.trace.TraceIds;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.p2.smtp.KeycloakSmtpHook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SessionLoginAuditTest {
    private static final String TRACE = "0199a1c0-0000-7000-8000-00000000c004";

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
        TraceIds.clearMdc();
    }

    @Test
    void loginLogoutAndRejectLandOnHashChainByTrace() {
        AuditPort audits = new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        StubIdentityProvider idp = new StubIdentityProvider();
        IdentityModule module = new IdentityModule(
                new InMemoryIdentityRepository(),
                idp,
                new KeycloakSmtpHook(idp, false),
                Clock.systemUTC(),
                Duration.ofHours(8),
                audits);
        var tenant = java.util.UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        module.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenant, "G4 Admin", "g4-login@example.com", "g4-login"));

        TraceIds.putMdc(TRACE);
        String token = StubIdentityProvider.token("g4-login", "g4-login@example.com", "g4-login", "G4 Admin");
        SessionService.SessionView view = module.sessions().loginWithAccessToken(token);
        assertEquals(tenant, view.tenantId());

        List<ChainedAuditEvent> accepted = audits.findByTraceId(TRACE);
        assertTrue(accepted.stream().anyMatch(event -> AuditCaptures.LOGIN_ACCEPTED.equals(event.event().eventType())),
                accepted.toString());

        module.sessions().logout(module.sessions().authenticate(token), null, null);
        assertTrue(audits.findByTraceId(TRACE).stream()
                .anyMatch(event -> AuditCaptures.LOGIN_LOGOUT.equals(event.event().eventType())));

        String rejectTrace = "0199a1c0-0000-7000-8000-00000000c005";
        TraceIds.putMdc(rejectTrace);
        assertThrows(one.nexus.p2.support.DomainException.class,
                () -> module.sessions().loginWithAccessToken("not-a-stub-token"));
        List<ChainedAuditEvent> rejected = audits.findByTraceId(rejectTrace);
        assertEquals(1, rejected.size());
        assertEquals(AuditCaptures.LOGIN_REJECTED, rejected.getFirst().event().eventType());
        assertTrue(audits.verifyChain().intact());
    }
}
