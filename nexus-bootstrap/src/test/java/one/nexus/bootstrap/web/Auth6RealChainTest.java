package one.nexus.bootstrap.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import one.nexus.p1.config.P1Properties;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.infra.isolation.IsolationContextHolder;
import one.nexus.p1.infra.isolation.ThreadLocalIsolationGuard;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.p2.session.SessionService;
import one.nexus.p2.smtp.KeycloakSmtpHook;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.PlatformDutyCatalog;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * AUTH6F3: the platform read bridge against the real IAM session and the real permission engine (no mocks between
 * the filter and the duty / grant decision). Covers the switch, the authenticated platform operator and outsiders.
 */
class Auth6RealChainTest {
    static final UUID OTHER_TENANT = UUID.fromString("01900000-0000-7000-8000-00000000f301");

    final P1Properties properties = new P1Properties();
    final UUID platform = properties.getPlatformTenant().getId();
    IdentityModule iam;
    PermissionModule permissions;
    SessionService.SessionView ops;
    String opsToken;

    @BeforeEach
    void setUp() {
        StubIdentityProvider idp = new StubIdentityProvider();
        iam = new IdentityModule(new InMemoryIdentityRepository(), idp, new KeycloakSmtpHook(idp, true),
                Clock.systemUTC(), Duration.ofMinutes(10));
        iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                platform, "Platform Ops", "ops@example.test", "ops-sub"));
        opsToken = StubIdentityProvider.token("ops-sub", "ops@example.test", "ops", "Platform Ops");
        ops = iam.sessions().loginWithAccessToken(opsToken);
        TenantContextHolder.clear();
        permissions = PermissionModule.create(new InMemoryPolicyRepository(), iam.repository(),
                new InMemoryPolicyInvalidation());
    }

    @AfterEach
    void clean() {
        TenantContextHolder.clear();
        IsolationContextHolder.clear();
    }

    /** Same steps as the DEMO-U2 window: install the three duties, then assign the operator to system admin. */
    void installDutiesAndAssignOperator() {
        TenantContextHolder.set(new TenantContext(UUID.randomUUID(), platform, null, ops.personId(),
                "platform-duty-bootstrap", Instant.now(), Instant.now().plusSeconds(60)));
        try {
            permissions.duties().install();
            var system = permissions.repository().findRoleByCode(platform, PlatformDutyCatalog.SYSTEM_ADMIN).orElseThrow();
            permissions.admin().assign("user", ops.personId().toString(), system.id());
        } finally {
            TenantContextHolder.clear();
        }
    }

    P1PlatformReadContextFilter filter(boolean enabled) {
        return new P1PlatformReadContextFilter(iam, permissions, properties, enabled);
    }

    static MockHttpServletRequest list(String path, String token, UUID contextId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        if (token != null) request.addHeader("Authorization", "Bearer " + token);
        if (contextId != null) request.addHeader("X-Nexus-Context-Id", contextId.toString());
        return request;
    }

    /** What the P1 list service does first: require an authenticated platform context. */
    static PlatformDuty requirePlatform() {
        return new ThreadLocalIsolationGuard().requirePlatformContext("tenant.list", PlatformDuty.SYSTEM_ADMIN)
                .actor().duties().iterator().next();
    }

    @Test
    void switchOffDoesNotBridgeSoTheListStillHasNoPlatformContext() throws Exception {
        installDutiesAndAssignOperator();
        var off = filter(false);
        var request = list("/api/v1/p1/tenants", opsToken, ops.contextId());
        assertTrue(off.shouldNotFilter(request));
        var response = new MockHttpServletResponse();
        off.doFilter(request, response, (req, res) -> {
            assertNull(IsolationContextHolder.get());
            assertThrows(RuntimeException.class, Auth6RealChainTest::requirePlatform);
        });
    }

    @Test
    void switchOnLetsTheAuthenticatedPlatformOperatorReadBothLists() throws Exception {
        installDutiesAndAssignOperator();
        for (String path : new String[] {"/api/v1/p1/tenants", "/api/v1/p1/tenant-registrations"}) {
            var response = new MockHttpServletResponse();
            boolean[] reached = {false};
            filter(true).doFilter(list(path, opsToken, ops.contextId()), response, (req, res) -> {
                reached[0] = true;
                assertEquals(PlatformDuty.SYSTEM_ADMIN, requirePlatform());
                assertEquals(ops.personId(), IsolationContextHolder.get().actor().subjectId());
            });
            assertTrue(reached[0], path);
            assertEquals(200, response.getStatus(), path);
        }
        assertNull(IsolationContextHolder.get());
    }

    @Test
    void switchOnWithoutCredentialsIsTheMissingCredentialsDenial() throws Exception {
        installDutiesAndAssignOperator();
        // What the SPA sent before AUTH6F4: demo headers only, no Bearer, no issued context.
        var request = list("/api/v1/p1/tenants", null, null);
        request.addHeader("X-Nexus-Demo-Actor-Id", ops.personId().toString());
        request.addHeader("X-Nexus-Demo-Reason", "m0-12-ui");
        var response = new MockHttpServletResponse();
        filter(true).doFilter(request, response, (req, res) -> fail("demo headers must not open the list"));
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"reason\":\"missing_credentials\""));
    }

    @Test
    void switchOnButDutiesNotInstalledIsTheDutySetDenial() throws Exception {
        var response = new MockHttpServletResponse();
        filter(true).doFilter(list("/api/v1/p1/tenants", opsToken, ops.contextId()), response,
                (req, res) -> fail("an operator without platform_system_admin must not read"));
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"reason\":\"duty_set\""));
    }

    @Test
    void switchOnStillRefusesAMemberOfAnOrdinaryTenant() throws Exception {
        installDutiesAndAssignOperator();
        iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                OTHER_TENANT, "Acme Admin", "acme@example.test", "acme-sub"));
        String token = StubIdentityProvider.token("acme-sub", "acme@example.test", "acme", "Acme Admin");
        var member = iam.sessions().loginWithAccessToken(token);
        TenantContextHolder.clear();

        var response = new MockHttpServletResponse();
        filter(true).doFilter(list("/api/v1/p1/tenant-registrations", token, member.contextId()), response,
                (req, res) -> fail("a non-platform tenant must not read platform lists"));
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"reason\":\"not_platform_context\""));
    }

    @Test
    void switchOnRefusesAnotherIdentityPresentingTheOperatorsContext() throws Exception {
        installDutiesAndAssignOperator();
        String stranger = StubIdentityProvider.token("stranger-sub", "stranger@example.test", "stranger", "Stranger");
        var response = new MockHttpServletResponse();
        filter(true).doFilter(list("/api/v1/p1/tenants", stranger, ops.contextId()), response,
                (req, res) -> fail("a borrowed context must not read"));
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"reason\":\"session_forbidden\""));
    }
}
