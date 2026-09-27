package one.nexus.p2.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import one.nexus.p2.idp.KeycloakIdentityProvider;
import one.nexus.p2.idp.Pkce;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.p2.smtp.KeycloakSmtpHook;
import one.nexus.p2.support.DomainException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SessionLogoutEndSessionTest {
    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaac");
    private static final String AFTER = "https://app.example.test/login";

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    /** Stub IdP that also answers the RP-logout URL and records / fails back-channel logout on demand. */
    static final class LogoutIdp implements IdentityProviderPort {
        final StubIdentityProvider stub = new StubIdentityProvider();
        final List<String[]> backChannel = new ArrayList<>();
        boolean failBackChannel;

        @Override public AuthorizeUrl authorizationUrl(String state, String redirectUri) { return stub.authorizationUrl(state, redirectUri); }
        @Override public AuthorizeUrl authorizationUrl(String state, String redirectUri, String acr, Pkce.Challenge pkce) { return stub.authorizationUrl(state, redirectUri, acr, pkce); }
        @Override public TokenSet exchangeAuthorizationCode(String code, String redirectUri) { return stub.exchangeAuthorizationCode(code, redirectUri); }
        @Override public IdentityClaims validate(String accessToken) { return stub.validate(accessToken); }
        @Override public Optional<IdentityClaims> userInfo(String accessToken) { return stub.userInfo(accessToken); }
        @Override public ProvisionedUser ensureUser(String email, String username, String displayName) { return stub.ensureUser(email, username, displayName); }
        @Override public EmailAction sendExecuteActionsEmail(String subject, List<String> actions, String kind) { return stub.sendExecuteActionsEmail(subject, actions, kind); }
        @Override public List<DirectoryUser> listUsers() { return stub.listUsers(); }
        @Override public Optional<DirectoryUser> findUser(String subject) { return stub.findUser(subject); }

        @Override
        public void logout(String refreshToken, String idTokenHint) {
            backChannel.add(new String[] {refreshToken, idTokenHint});
            if (failBackChannel) {
                throw new IllegalStateException("idp unreachable");
            }
        }

        @Override
        public Optional<String> endSessionUrl(String idTokenHint, String postLogoutRedirectUri) {
            return Optional.of("https://id.example.test/realms/nexus/protocol/openid-connect/logout?id_token_hint=h&post_logout_redirect_uri="
                    + java.net.URLEncoder.encode(postLogoutRedirectUri, java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static IdentityModule module(LogoutIdp idp) {
        IdentityModule module = new IdentityModule(
                new InMemoryIdentityRepository(), idp, new KeycloakSmtpHook(idp, false),
                Clock.systemUTC(), Duration.ofHours(8));
        module.adminInitializer().initialize(new TenantAdminInitializer.Command(
                TENANT, "LO3 Admin", "lo3@example.com", "lo3"));
        return module;
    }

    @Test
    void withAHintLogoutClearsTheSessionCallsBackChannelAndReturnsTheUrl() {
        LogoutIdp idp = new LogoutIdp();
        IdentityModule module = module(idp);
        SessionService.SessionView view = module.sessions().loginWithCode("stub-code", "https://app.example.test/callback");
        SessionService.AuthenticatedIdentity identity = module.sessions().authenticate(view.accessToken());

        SessionService.LogoutResult result = module.sessions().logout(identity, null, null, AFTER);

        assertTrue(result.hasEndSessionUrl());
        assertTrue(result.endSessionUrl().contains("/protocol/openid-connect/logout"));
        assertTrue(result.endSessionUrl().contains("post_logout_redirect_uri=" + java.net.URLEncoder.encode(AFTER, java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals(1, idp.backChannel.size());
        assertEquals("stub-refresh", idp.backChannel.getFirst()[0]);
        assertNotNull(idp.backChannel.getFirst()[1]);
        assertThrows(DomainException.class, () -> module.sessions().loadContext(view.contextId(), identity));
        assertFalse(module.sessions().logoutHints().holds(identity.claims().subject()));
    }

    @Test
    void withoutAHintTheSessionIsStillClearedAndTheUrlIsEmpty() {
        LogoutIdp idp = new LogoutIdp();
        IdentityModule module = module(idp);
        SessionService.SessionView first = module.sessions().loginWithCode("stub-code", "https://app.example.test/callback");
        SessionService.AuthenticatedIdentity identity = module.sessions().authenticate(first.accessToken());
        module.sessions().logout(identity, null, null, AFTER);

        // The hint was spent by the first logout; a token-only login brings none.
        SessionService.SessionView again = module.sessions().loginWithAccessToken(first.accessToken());
        SessionService.LogoutResult result = module.sessions().logout(identity, null, null, AFTER);

        assertEquals("", result.endSessionUrl());
        assertEquals(1, idp.backChannel.size(), "no hint → no back-channel call");
        assertThrows(DomainException.class, () -> module.sessions().loadContext(again.contextId(), identity));
    }

    @Test
    void aFailingBackChannelNeitherBlocksLogoutNorDropsTheUrl() {
        LogoutIdp idp = new LogoutIdp();
        idp.failBackChannel = true;
        IdentityModule module = module(idp);
        SessionService.SessionView view = module.sessions().loginWithCode("stub-code", "https://app.example.test/callback");
        SessionService.AuthenticatedIdentity identity = module.sessions().authenticate(view.accessToken());

        SessionService.LogoutResult result = module.sessions().logout(identity, null, null, AFTER);

        assertTrue(result.hasEndSessionUrl());
        assertThrows(DomainException.class, () -> module.sessions().loadContext(view.contextId(), identity));
    }

    @Test
    void withoutAnAllowedRedirectThereIsNoUrl() {
        LogoutIdp idp = new LogoutIdp();
        IdentityModule module = module(idp);
        SessionService.SessionView view = module.sessions().loginWithCode("stub-code", "https://app.example.test/callback");
        SessionService.AuthenticatedIdentity identity = module.sessions().authenticate(view.accessToken());
        assertEquals("", module.sessions().logout(identity, null, null, null).endSessionUrl());
    }

    @Test
    void keycloakEndSessionUrlCarriesClientHintAndRedirectButNeverTheSecret() {
        String secret = "client-secret-must-not-leak";
        KeycloakIdentityProvider keycloak = new KeycloakIdentityProvider(
                "https://id.example.test/realms/nexus/", "nexus-web", secret);

        String url = keycloak.endSessionUrl("id.token.hint", AFTER).orElseThrow();

        assertTrue(url.startsWith("https://id.example.test/realms/nexus/protocol/openid-connect/logout?"));
        assertTrue(url.contains("client_id=nexus-web"));
        assertTrue(url.contains("id_token_hint=id.token.hint"));
        assertTrue(url.contains("post_logout_redirect_uri=https%3A%2F%2Fapp.example.test%2Flogin"));
        assertFalse(url.contains(secret));
        assertFalse(url.contains("client_secret"));
        assertTrue(keycloak.endSessionUrl(null, AFTER).isEmpty());
        assertTrue(keycloak.endSessionUrl("id.token.hint", " ").isEmpty());
    }
}
