package one.nexus.p2.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.p2.smtp.KeycloakSmtpHook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/** DEMO-U6: membership views carry tenant code / name for display; tenant_id is unchanged; labels never block login. */
class SessionTenantLabelsTest {
    private static final UUID TENANT = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    private static IdentityModule module() {
        StubIdentityProvider idp = new StubIdentityProvider();
        IdentityModule module = new IdentityModule(new InMemoryIdentityRepository(), idp, new KeycloakSmtpHook(idp, false),
                Clock.systemUTC(), Duration.ofHours(8));
        module.adminInitializer().initialize(new TenantAdminInitializer.Command(TENANT, "Label Admin", "u6@example.com", "u6"));
        return module;
    }

    private static String token() {
        return StubIdentityProvider.token("u6", "u6@example.com", "u6", "Label Admin");
    }

    @Test
    void membershipsCarryCodeAndNameAndKeepTheTenantId() throws Exception {
        IdentityModule module = module();
        module.sessions().useTenantLabels(id -> id.equals(TENANT)
                ? Optional.of(new TenantLabelPort.TenantLabel("demo_acme", "演示租户 · 星河科技")) : Optional.empty());
        SessionService.SessionView view = module.sessions().loginWithAccessToken(token());
        SessionService.MembershipView membership = view.memberships().getFirst();
        assertEquals(TENANT, membership.tenantId());
        assertEquals("demo_acme", membership.tenantCode());
        assertEquals("演示租户 · 星河科技", membership.tenantName());

        ObjectMapper mapper = JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
        JsonNode wire = mapper.readTree(mapper.writeValueAsString(view)).path("memberships").get(0);
        assertEquals(TENANT.toString(), wire.path("tenant_id").asText());
        assertEquals("demo_acme", wire.path("tenant_code").asText());
        assertEquals("演示租户 · 星河科技", wire.path("tenant_name").asText());

        SessionService.SessionView switched = module.sessions().switchTenant(module.sessions().authenticate(token()), TENANT);
        assertEquals("demo_acme", switched.memberships().getFirst().tenantCode());
    }

    @Test
    void withoutOrWithABrokenLabelSourceLoginStillWorksAndLabelsAreAbsent() {
        IdentityModule module = module();
        SessionService.SessionView plain = module.sessions().loginWithAccessToken(token());
        assertNull(plain.memberships().getFirst().tenantCode());
        assertNull(plain.memberships().getFirst().tenantName());

        module.sessions().useTenantLabels(id -> { throw new IllegalStateException("tenant table unavailable"); });
        SessionService.SessionView view = module.sessions().loginWithAccessToken(token());
        assertEquals(TENANT, view.memberships().getFirst().tenantId());
        assertNull(view.memberships().getFirst().tenantName());
        assertTrue(view.accessToken() != null && !view.accessToken().isBlank());
    }
}
