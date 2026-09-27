package one.nexus.p2.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.p2.smtp.KeycloakSmtpHook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/** AUTH1: login responses carry the validated access token; switch and current do not. */
class SessionAccessTokenTest {
    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void loginResponseContainsNonEmptyAccessToken() throws Exception {
        IdentityModule module = module();
        UUID tenant = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        module.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenant, "Auth Admin", "auth1@example.com", "auth1"));
        String token = StubIdentityProvider.token("auth1", "auth1@example.com", "auth1", "Auth Admin");

        SessionService.SessionView view = module.sessions().loginWithAccessToken(token);

        assertEquals(token, view.accessToken());
        assertFalse(view.accessToken().isBlank());
        ObjectMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        assertEquals(token, mapper.readTree(mapper.writeValueAsString(view)).path("access_token").asText());

        SessionService.SessionView current = module.sessions().current(module.sessions().authenticate(token));
        assertNull(current.accessToken());
        SessionService.SessionView switched = module.sessions().switchTenant(
                module.sessions().authenticate(token), tenant);
        assertNull(switched.accessToken());
    }

    @Test
    void codeLoginReturnsTheTokenThatWasValidated() {
        IdentityModule module = module();
        UUID tenant = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
        module.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenant, "Code Admin", "auth1-code@example.com", "auth1-code"));

        SessionService.SessionView view = module.sessions().loginWithCode("stub-code", "http://127.0.0.1/cb");

        assertFalse(view.accessToken() == null || view.accessToken().isBlank());
        module.sessions().authenticate(view.accessToken());
    }

    private static IdentityModule module() {
        StubIdentityProvider idp = new StubIdentityProvider();
        return new IdentityModule(
                new InMemoryIdentityRepository(),
                idp,
                new KeycloakSmtpHook(idp, false),
                Clock.systemUTC(),
                Duration.ofHours(8));
    }
}
