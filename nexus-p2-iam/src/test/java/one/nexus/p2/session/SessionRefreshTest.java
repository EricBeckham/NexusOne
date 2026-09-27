package one.nexus.p2.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.IdentityProviderPort;
import one.nexus.p2.idp.Pkce;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.p2.smtp.KeycloakSmtpHook;
import one.nexus.p2.support.DomainException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SessionRefreshTest {
    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa7001");

    /** Stub IdP whose refresh grant issues a fresh (different) access token and rotates the refresh token. */
    static final class RefreshingIdp implements IdentityProviderPort {
        final StubIdentityProvider stub = new StubIdentityProvider();
        final List<String> refreshedWith = new ArrayList<>();
        final List<String> backChannelRefresh = new ArrayList<>();
        boolean fail;
        int round;

        @Override public AuthorizeUrl authorizationUrl(String state, String redirectUri) { return stub.authorizationUrl(state, redirectUri); }
        @Override public AuthorizeUrl authorizationUrl(String state, String redirectUri, String acr, Pkce.Challenge pkce) { return stub.authorizationUrl(state, redirectUri, acr, pkce); }
        @Override public TokenSet exchangeAuthorizationCode(String code, String redirectUri) { return stub.exchangeAuthorizationCode(code, redirectUri); }
        @Override public IdentityClaims validate(String accessToken) { return stub.validate(accessToken); }
        @Override public Optional<IdentityClaims> userInfo(String accessToken) { return stub.userInfo(accessToken); }
        @Override public ProvisionedUser ensureUser(String email, String username, String displayName) { return stub.ensureUser(email, username, displayName); }
        @Override public EmailAction sendExecuteActionsEmail(String subject, List<String> actions, String kind) { return stub.sendExecuteActionsEmail(subject, actions, kind); }
        @Override public List<DirectoryUser> listUsers() { return stub.listUsers(); }
        @Override public Optional<DirectoryUser> findUser(String subject) { return stub.findUser(subject); }
        @Override public void logout(String refreshToken, String idTokenHint) { backChannelRefresh.add(refreshToken); }

        @Override
        public TokenSet refresh(String refreshToken) {
            refreshedWith.add(refreshToken);
            if (fail) {
                throw new IllegalStateException("idp refused");
            }
            round++;
            IdentityClaims claims = stub.listUsers().getFirst().toClaims();
            String access = StubIdentityProvider.token(claims.subject(), claims.email(), claims.username(),
                    claims.displayName(), "mfa", "otp refresh-" + round);
            return new TokenSet(access, "rotated-refresh-" + round, null, "Bearer", 300);
        }
    }

    RefreshingIdp idp;
    IdentityModule module;

    @BeforeEach
    void setUp() {
        idp = new RefreshingIdp();
        module = new IdentityModule(new InMemoryIdentityRepository(), idp, new KeycloakSmtpHook(idp, false),
                Clock.systemUTC(), Duration.ofHours(8));
        module.adminInitializer().initialize(new TenantAdminInitializer.Command(
                TENANT, "R1 Admin", "r1@example.com", "r1"));
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    SessionService.SessionView login() {
        SessionService.SessionView view = module.sessions().loginWithCode("stub-code", "https://app.example.test/callback");
        TenantContextHolder.clear();
        return view;
    }

    static String code(Runnable call) {
        return assertThrows(DomainException.class, call::run).code();
    }

    @Test
    void renewsWithTheServerHeldRefreshTokenAndKeepsTheTenantContext() {
        SessionService.SessionView view = login();

        SessionService.RefreshView renewed = module.sessions().refresh(view.accessToken());

        assertNotEquals(view.accessToken(), renewed.accessToken());
        assertEquals(List.of("stub-refresh"), idp.refreshedWith);
        // The issued TenantContext is bound to the subject, so it keeps working with the renewed token.
        var identity = module.sessions().authenticate(renewed.accessToken());
        assertEquals(view.contextId(), module.sessions().loadContext(view.contextId(), identity).contextId());
    }

    @Test
    void theHintIsSingleUseAndFollowsTheRotatedRefreshToken() {
        SessionService.SessionView view = login();
        String first = module.sessions().refresh(view.accessToken()).accessToken();

        // Replaying the old access token cannot refresh again.
        assertEquals("P2.IAM.REFRESH_UNAVAILABLE", code(() -> module.sessions().refresh(view.accessToken())));

        String second = module.sessions().refresh(first).accessToken();
        assertNotEquals(first, second);
        assertEquals(List.of("stub-refresh", "rotated-refresh-1"), idp.refreshedWith);

        // Logout with the latest token still finds its hint (the rotated refresh token goes to the back-channel).
        TenantContextHolder.clear();
        var identity = module.sessions().authenticate(second);
        module.sessions().loadContext(view.contextId(), identity);
        module.sessions().logout(identity, null, null, "https://app.example.test/login");
        assertEquals(List.of("rotated-refresh-2"), idp.backChannelRefresh);
    }

    @Test
    void anIdpRefusalMeansSignInAgainAndLeavesNoHint() {
        SessionService.SessionView view = login();
        idp.fail = true;
        assertEquals("P2.IAM.REFRESH_FAILED", code(() -> module.sessions().refresh(view.accessToken())));
        idp.fail = false;
        assertEquals("P2.IAM.REFRESH_UNAVAILABLE", code(() -> module.sessions().refresh(view.accessToken())));
    }

    @Test
    void withoutAServerHintThereIsNothingToRefresh() {
        String token = StubIdentityProvider.token("direct", "direct@example.com", "direct", "Direct");
        module.sessions().loginWithAccessToken(token);
        TenantContextHolder.clear();
        assertEquals("P2.IAM.REFRESH_UNAVAILABLE", code(() -> module.sessions().refresh(token)));
        assertEquals("P2.IAM.REFRESH_UNAVAILABLE", code(() -> module.sessions().refresh(null)));
        assertEquals("P2.IAM.REFRESH_UNAVAILABLE", code(() -> module.sessions().refresh(" ")));
        assertTrue(idp.refreshedWith.isEmpty());
    }

    @Test
    void theRefreshAnswerNeverPrintsTheToken() {
        var view = new SessionService.RefreshView("secret-access-token");
        assertFalse(view.toString().contains("secret-access-token"));
    }
}
