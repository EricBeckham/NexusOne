package one.nexus.p2.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.p2.smtp.KeycloakSmtpHook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LogoutHintStoreTest {
    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaab");

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    private static IdentityModule module(StubIdentityProvider idp) {
        IdentityModule module = new IdentityModule(
                new InMemoryIdentityRepository(), idp, new KeycloakSmtpHook(idp, false),
                Clock.systemUTC(), Duration.ofHours(8));
        module.adminInitializer().initialize(new TenantAdminInitializer.Command(
                TENANT, "LO2 Admin", "lo2@example.com", "lo2"));
        return module;
    }

    @Test
    void authCodeLoginKeepsTheIdpHintServerSideAndLogoutSpendsIt() {
        IdentityModule module = module(new StubIdentityProvider());
        SessionService.SessionView view = module.sessions().loginWithCode("stub-code", "https://app.example.test/callback");
        assertNotNull(view.contextId());

        SessionService.AuthenticatedIdentity identity = module.sessions().authenticate(view.accessToken());
        String subject = identity.claims().subject();
        assertTrue(module.sessions().logoutHints().holds(subject));

        module.sessions().logout(identity, null, null);
        assertFalse(module.sessions().logoutHints().holds(subject));
        assertTrue(module.sessions().logoutHints().take(subject).isEmpty());
    }

    @Test
    void theLoginResponseCarriesNoIdTokenOrRefreshToken() {
        String[] components = Arrays.stream(SessionService.SessionView.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toArray(String[]::new);
        assertFalse(Arrays.asList(components).contains("idToken"));
        assertFalse(Arrays.asList(components).contains("refreshToken"));
    }

    @Test
    void accessTokenLoginDoesNotInventAHint() {
        IdentityModule module = module(new StubIdentityProvider());
        String token = StubIdentityProvider.token("lo2-direct", "lo2-direct@example.com", "lo2-direct", "Direct");
        module.sessions().loginWithAccessToken(token);
        assertFalse(module.sessions().logoutHints().holds("lo2-direct"));
    }

    @Test
    void hintIsSingleUseAndExpires() {
        Instant now = Instant.parse("2026-09-27T08:00:00Z");
        LogoutHintStore live = new LogoutHintStore(Clock.fixed(now, ZoneOffset.UTC));
        live.remember("sub-1", "id-token-value", "refresh-token-value", now.plusSeconds(60));
        LogoutHintStore.Hint hint = live.take("sub-1").orElseThrow();
        assertEquals("id-token-value", hint.idToken());
        assertTrue(live.take("sub-1").isEmpty());

        LogoutHintStore late = new LogoutHintStore(Clock.fixed(now.plusSeconds(61), ZoneOffset.UTC));
        late.remember("sub-2", "id-token-value", null, now.plusSeconds(60));
        assertFalse(late.holds("sub-2"));
        assertTrue(late.take("sub-2").isEmpty());
    }

    @Test
    void blankTokensAreNotKeptAndToStringNeverShowsValues() {
        LogoutHintStore store = new LogoutHintStore(Clock.systemUTC());
        store.remember("sub-3", " ", null, Instant.now().plusSeconds(60));
        assertFalse(store.holds("sub-3"));

        LogoutHintStore.Hint hint = new LogoutHintStore.Hint("id-token-value", "refresh-token-value", Instant.EPOCH);
        assertFalse(hint.toString().contains("id-token-value"));
        assertFalse(hint.toString().contains("refresh-token-value"));
    }
}
