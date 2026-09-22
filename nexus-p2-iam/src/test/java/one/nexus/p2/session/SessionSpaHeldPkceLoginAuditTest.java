package one.nexus.p2.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import one.nexus.p2.idp.Pkce;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.p2.smtp.KeycloakSmtpHook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * G4 leftover: SPA-minted S256 verifier. The page holds {@code code_verifier};
 * authorize-url only echoes {@code code_challenge}. Stub IdP (no Keycloak).
 * Does not redo {@code SessionPkceLoginAuditTest} BFF mint.
 */
class SessionSpaHeldPkceLoginAuditTest {
    static final String TRACE = "0199a1c0-0000-7000-8000-00000000c21a";

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
        TraceIds.clearMdc();
    }

    @Test
    void spaHeldChallengeDoesNotMintVerifierAndStillLandsLogin() {
        AuditPort audits = new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        StubIdentityProvider idp = new StubIdentityProvider();
        IdentityModule module = new IdentityModule(
                new InMemoryIdentityRepository(),
                idp,
                new KeycloakSmtpHook(idp, false),
                Clock.systemUTC(),
                Duration.ofHours(8),
                audits);
        var tenant = java.util.UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa1a");
        module.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenant, "G4 SPA PKCE Admin", "g4-spa-pkce@example.com", "g4-spa-pkce"));

        TraceIds.putMdc(TRACE);
        Pkce.Challenge minted = Pkce.s256();
        Pkce.Challenge held = Pkce.clientHeld(minted.challenge());
        assertTrue(Pkce.clientHeld(held));
        assertTrue(held.verifier() == null || held.verifier().isBlank());

        var authorize = module.sessions().authorizationUrl("state-spa", "http://127.0.0.1/cb", null, held);
        assertTrue(authorize.url().contains("code=stub-code"), authorize.url());
        assertTrue(authorize.url().contains("code_challenge=" + minted.challenge()), authorize.url());
        assertFalse(authorize.url().contains("code_verifier="), authorize.url());

        SessionService.SessionView view = module.sessions()
                .loginWithCode("stub-code", "http://127.0.0.1/cb", minted.verifier());
        assertEquals(tenant, view.tenantId());

        List<ChainedAuditEvent> found = audits.findByTraceId(TRACE);
        assertTrue(found.stream().anyMatch(event -> AuditCaptures.LOGIN_ACCEPTED.equals(event.event().eventType())),
                found.toString());
        assertTrue(audits.verifyChain().intact(), audits.verifyChain().failure());
    }
}
