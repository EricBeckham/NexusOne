package one.nexus.bootstrap.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * AUTH6G4: every AUTH6G2 / G3 route against the real IAM session and permission engine — missing credentials,
 * invalid context, wrong duty, success, and the route boundary (unlisted reads and all writes are never bridged).
 */
class Auth6RouteRealChainTest {
    static final String ID = "01900000-0000-7000-8000-000000000101";
    static final UUID OTHER_TENANT = UUID.fromString("01900000-0000-7000-8000-00000000f401");

    final P1Properties properties = new P1Properties();
    final UUID platform = properties.getPlatformTenant().getId();
    IdentityModule iam;
    PermissionModule permissions;
    SessionService.SessionView person;
    String token;

    @BeforeEach
    void setUp() {
        StubIdentityProvider idp = new StubIdentityProvider();
        iam = new IdentityModule(new InMemoryIdentityRepository(), idp, new KeycloakSmtpHook(idp, true),
                Clock.systemUTC(), Duration.ofMinutes(10));
        iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                platform, "Platform Person", "platform@example.test", "platform-sub"));
        token = StubIdentityProvider.token("platform-sub", "platform@example.test", "platform", "Platform Person");
        person = iam.sessions().loginWithAccessToken(token);
        TenantContextHolder.clear();
        permissions = PermissionModule.create(new InMemoryPolicyRepository(), iam.repository(),
                new InMemoryPolicyInvalidation());
    }

    @AfterEach
    void clean() {
        TenantContextHolder.clear();
        IsolationContextHolder.clear();
    }

    /** DEMO-U2 steps, assigning the platform person to exactly one duty role. */
    void assignOnly(String dutyRole) {
        TenantContextHolder.set(new TenantContext(UUID.randomUUID(), platform, null, person.personId(),
                "platform-duty-bootstrap", Instant.now(), Instant.now().plusSeconds(60)));
        try {
            permissions.duties().install();
            var role = permissions.repository().findRoleByCode(platform, dutyRole).orElseThrow();
            permissions.admin().assign("user", person.personId().toString(), role.id());
        } finally {
            TenantContextHolder.clear();
        }
    }

    P1PlatformReadContextFilter bridge() {
        return new P1PlatformReadContextFilter(iam, permissions, properties, true);
    }

    static MockHttpServletRequest request(String method, String uri, String bearer, String contextId) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        if (bearer != null) request.addHeader("Authorization", "Bearer " + bearer);
        if (contextId != null) request.addHeader("X-Nexus-Context-Id", contextId);
        return request;
    }

    MockHttpServletResponse refused(MockHttpServletRequest request, int status, String reason) throws Exception {
        var response = new MockHttpServletResponse();
        bridge().doFilter(request, response, (req, res) -> fail("must not reach " + request.getRequestURI()));
        assertEquals(status, response.getStatus(), request.getRequestURI());
        assertTrue(response.getContentAsString().contains("\"reason\":\"" + reason + "\""), response.getContentAsString());
        return response;
    }

    // ---- AUTH6G2: SYSTEM_ADMIN routes -------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "/api/v1/p1/tenants/tree, tenant.relation.tree",
            "/api/v1/p1/tenants/" + ID + "/children, tenant.relation.children",
            "/api/v1/p1/tenants/" + ID + "/ancestors, tenant.relation.ancestors",
            "/api/v1/p1/platform/services, platform.service.status",
            "/api/v1/p1/platform/form-templates, form-template.list",
            "/api/v1/p1/platform/form-templates/" + ID + ", form-template.get",
    })
    void systemAdminRoutesPassForTheSystemAdmin(String uri, String operation) throws Exception {
        assignOnly(PlatformDutyCatalog.SYSTEM_ADMIN);
        var response = new MockHttpServletResponse();
        boolean[] reached = {false};
        bridge().doFilter(request("GET", uri, token, person.contextId().toString()), response, (req, res) -> {
            reached[0] = true;
            // What the controller / service asks for must now succeed.
            new ThreadLocalIsolationGuard().requirePlatformContext(operation, PlatformDuty.SYSTEM_ADMIN);
            assertEquals(Set.of(PlatformDuty.SYSTEM_ADMIN), IsolationContextHolder.get().actor().duties());
            assertEquals(person.personId(), IsolationContextHolder.get().actor().subjectId());
        });
        assertTrue(reached[0], uri);
        assertEquals(200, response.getStatus());
        assertNull(IsolationContextHolder.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/p1/tenants/tree", "/api/v1/p1/platform/services", "/api/v1/p1/platform/form-templates",
            "/api/v1/p1/tenants/" + ID + "/children"})
    void systemAdminRoutesRefuseMissingCredentialsInvalidContextAndWrongDuty(String uri) throws Exception {
        refused(request("GET", uri, null, null), 401, "missing_credentials");
        refused(request("GET", uri, token, null), 401, "missing_credentials");
        refused(request("GET", uri, token, UUID.randomUUID().toString()), 401, "session_invalid");
        refused(request("GET", uri, token, "not-a-context-id"), 401, "session_invalid");

        assignOnly(PlatformDutyCatalog.AUDIT_ADMIN);
        refused(request("GET", uri, token, person.contextId().toString()), 403, "duty_set");
    }

    @Test
    void systemAdminRoutesRefuseAnOrdinaryTenant() throws Exception {
        assignOnly(PlatformDutyCatalog.SYSTEM_ADMIN);
        iam.adminInitializer().initialize(new TenantAdminInitializer.Command(
                OTHER_TENANT, "Acme Admin", "acme@example.test", "acme-sub"));
        String acme = StubIdentityProvider.token("acme-sub", "acme@example.test", "acme", "Acme Admin");
        var member = iam.sessions().loginWithAccessToken(acme);
        TenantContextHolder.clear();
        refused(request("GET", "/api/v1/p1/tenants/tree", acme, member.contextId().toString()), 403,
                "not_platform_context");
    }

    // ---- AUTH6G3: AUDIT_ADMIN route ---------------------------------------------------------------------------

    @Test
    void auditSearchPassesForTheAuditAdminWithAuditDutyOnly() throws Exception {
        assignOnly(PlatformDutyCatalog.AUDIT_ADMIN);
        var response = new MockHttpServletResponse();
        boolean[] reached = {false};
        bridge().doFilter(request("GET", "/api/v1/audit/events", token, person.contextId().toString()), response,
                (req, res) -> {
                    reached[0] = true;
                    var guard = new ThreadLocalIsolationGuard();
                    guard.requirePlatformContext("audit.events.search", PlatformDuty.AUDIT_ADMIN);
                    assertEquals(Set.of(PlatformDuty.AUDIT_ADMIN), IsolationContextHolder.get().actor().duties());
                    // Never a system-admin binding on the audit route.
                    assertThrows(RuntimeException.class,
                            () -> guard.requirePlatformContext("tenant.list", PlatformDuty.SYSTEM_ADMIN));
                });
        assertTrue(reached[0]);
        assertEquals(200, response.getStatus());
    }

    @Test
    void auditSearchRefusesTheSystemAdminAndMissingCredentials() throws Exception {
        assignOnly(PlatformDutyCatalog.SYSTEM_ADMIN);
        refused(request("GET", "/api/v1/audit/events", token, person.contextId().toString()), 403, "duty_set");
        refused(request("GET", "/api/v1/audit/events", null, null), 401, "missing_credentials");
        refused(request("GET", "/api/v1/audit/events", token, UUID.randomUUID().toString()), 401, "session_invalid");
    }

    @Test
    void auditSearchRefusesAnAuditorWithoutTheAuditGrant() throws Exception {
        assignOnly(PlatformDutyCatalog.AUDIT_ADMIN);
        // Deny audit:read for the audit role: the duty alone is not enough, the permission is checked too.
        TenantContextHolder.set(new TenantContext(UUID.randomUUID(), platform, null, person.personId(),
                "test", Instant.now(), Instant.now().plusSeconds(60)));
        try {
            permissions.admin().grant("DENY", "role", PlatformDutyCatalog.AUDIT_ADMIN,
                    PlatformDutyCatalog.RESOURCE_AUDIT, PlatformDutyCatalog.ACTION_READ,
                    one.nexus.permission.engine.DataScopeKind.ALL, java.util.List.of(), java.util.Map.of());
        } finally {
            TenantContextHolder.clear();
        }
        refused(request("GET", "/api/v1/audit/events", token, person.contextId().toString()), 403, "permission_denied");
    }

    // ---- Route boundary -----------------------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "GET, /api/v1/p1/tenants/" + ID,
            "PATCH, /api/v1/p1/tenants/" + ID + "/parent",
            "POST, /api/v1/p1/platform/form-templates",
            "PUT, /api/v1/p1/platform/form-templates/" + ID,
            "POST, /api/v1/p1/platform/form-templates/" + ID + "/publish",
            "POST, /api/v1/audit/chain/verify",
            "GET, /api/v1/audit/traces/abc",
    })
    void unlistedReadsAndAllWritesAreNeverBridged(String method, String uri) throws Exception {
        assignOnly(PlatformDutyCatalog.SYSTEM_ADMIN);
        var request = request(method, uri, token, person.contextId().toString());
        assertTrue(bridge().shouldNotFilter(request), method + " " + uri);
        boolean[] reached = {false};
        bridge().doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            reached[0] = true;
            assertNull(IsolationContextHolder.get(), "no platform context for " + method + " " + uri);
        });
        assertTrue(reached[0]);
    }

    @Test
    void switchOffBridgesNothing() throws Exception {
        assignOnly(PlatformDutyCatalog.SYSTEM_ADMIN);
        var off = new P1PlatformReadContextFilter(iam, permissions, properties, false);
        for (String uri : new String[] {"/api/v1/p1/tenants/tree", "/api/v1/p1/platform/services", "/api/v1/audit/events"}) {
            var request = request("GET", uri, token, person.contextId().toString());
            assertTrue(off.shouldNotFilter(request), uri);
            off.doFilter(request, new MockHttpServletResponse(), (req, res) -> assertNull(IsolationContextHolder.get()));
        }
    }
}
