package one.nexus.p2.session;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.api.ApiErrorHandler;
import one.nexus.p2.domain.Model;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.p2.smtp.KeycloakSmtpHook;
import one.nexus.p2.support.DomainException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/** TC2: auth-code login distinguishes zero, one and multiple ACTIVE memberships. */
class SessionCodeMembershipContextTest {
    private final InMemoryIdentityRepository repository = new InMemoryIdentityRepository();
    private final StubIdentityProvider idp = new StubIdentityProvider();
    private final IdentityModule module = new IdentityModule(repository, idp,
            new KeycloakSmtpHook(idp, false), Clock.systemUTC(), Duration.ofHours(8));
    private final UUID account = module.sessions().loginWithAccessToken(
            StubIdentityProvider.token("tc2-user", "tc2@example.com", "tc2", "TC2 User")).accountId();

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    void singleActiveMembershipIssuesPersistedContextAndTenantOnWire() {
        UUID tenant = membership(Model.MembershipStatus.ACTIVE);
        membership(Model.MembershipStatus.SUSPENDED);

        var view = login();

        assertNotNull(view.contextId());
        assertEquals(tenant, view.tenantId());
        assertNotNull(view.personId());
        var context = module.sessions().loadContext(view.contextId(),
                module.sessions().authenticate(view.accessToken()));
        assertEquals(account, context.accountId());
        assertEquals(tenant, context.tenantId());
        assertEquals(view.personId(), context.personId());
        var mapper = JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
        var json = mapper.readTree(mapper.writeValueAsString(view));
        assertEquals(view.contextId().toString(), json.path("context_id").asText());
        assertEquals(tenant.toString(), json.path("tenant_id").asText());
    }

    @Test
    void noMembershipReturnsExplicitForbiddenCategory() {
        assertNoActiveMembership();
    }

    @Test
    void onlySuspendedMembershipReturnsSameExplicitCategory() {
        membership(Model.MembershipStatus.SUSPENDED);
        assertNoActiveMembership();
    }

    @Test
    void multipleActiveMembershipsStillRequireClientTenantSelection() {
        UUID first = membership(Model.MembershipStatus.ACTIVE);
        UUID second = membership(Model.MembershipStatus.ACTIVE);

        var view = login();

        assertNull(view.contextId());
        assertNull(view.tenantId());
        assertNull(view.personId());
        assertEquals(2, view.memberships().size());
        assertTrue(view.memberships().stream().anyMatch(item -> first.equals(item.tenantId())));
        assertTrue(view.memberships().stream().anyMatch(item -> second.equals(item.tenantId())));
        var switched = module.sessions().switchTenant(module.sessions().authenticate(view.accessToken()), second);
        assertNotNull(switched.contextId());
        assertEquals(second, switched.tenantId());
    }

    private void assertNoActiveMembership() {
        var error = assertThrows(DomainException.class, this::login);
        assertEquals("P2.IAM.NO_ACTIVE_MEMBERSHIP", error.code());
        assertEquals(403, error.httpStatus());
        var response = new ApiErrorHandler().handle(error);
        assertEquals(403, response.getStatusCode().value());
        assertEquals("P2.IAM.NO_ACTIVE_MEMBERSHIP", response.getBody().get("code"));
        assertNull(TenantContextHolder.get());
    }

    private SessionService.SessionView login() {
        return module.sessions().loginWithCode("stub-code", "http://127.0.0.1/callback", "tc2-verifier");
    }

    private UUID membership(Model.MembershipStatus status) {
        UUID tenant = UUID.randomUUID();
        Instant now = Instant.now();
        repository.saveMembership(new Model.TenantMembership(UUID.randomUUID(), tenant, account,
                status, now, now, null, 0));
        repository.savePerson(new Model.Person(UUID.randomUUID(), tenant, account, "TC2 User",
                "TC2", "tc2@example.com", null, null, Model.PersonStatus.ACTIVE, now, now, null, 0));
        return tenant;
    }
}
