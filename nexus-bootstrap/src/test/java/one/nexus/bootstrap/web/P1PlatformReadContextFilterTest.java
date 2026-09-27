package one.nexus.bootstrap.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import one.nexus.p1.api.TenantController;
import one.nexus.p1.api.TenantRegistrationController;
import one.nexus.p1.application.TenantLifecycleService;
import one.nexus.p1.application.TenantRegistrationService;
import one.nexus.p1.config.P1Properties;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.infra.isolation.DemoPlatformContextFilter;
import one.nexus.p1.infra.isolation.IsolationContextHolder;
import one.nexus.p1.infra.isolation.ThreadLocalIsolationGuard;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.session.SessionService;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.PermissionService;
import one.nexus.permission.admin.PlatformDutyCatalog;
import one.nexus.permission.engine.PolicySnapshot;
import one.nexus.permission.engine.Subject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

class P1PlatformReadContextFilterTest {
    final P1Properties properties = new P1Properties();
    final IdentityModule identity = mock(IdentityModule.class);
    final SessionService sessions = mock(SessionService.class);
    final PermissionModule permissions = mock(PermissionModule.class);
    final PermissionService service = mock(PermissionService.class);
    final UUID contextId = UUID.randomUUID();
    final UUID person = UUID.randomUUID();
    TenantContext context;
    P1PlatformReadContextFilter filter;
    MockMvc mvc;

    @BeforeEach void setup() {
        context = new TenantContext(contextId, properties.getPlatformTenant().getId(), UUID.randomUUID(),
                person, "subject", Instant.now(), Instant.now().plusSeconds(60));
        when(identity.sessions()).thenReturn(sessions);
        when(permissions.service()).thenReturn(service);
        when(sessions.loadContext(eq(contextId), any(SessionService.AuthenticatedIdentity.class)))
                .thenAnswer(invocation -> { TenantContextHolder.set(context); return context; });
        when(sessions.authenticate("test-token")).thenReturn(mock(SessionService.AuthenticatedIdentity.class));
        roles(Set.of(PlatformDutyCatalog.SYSTEM_ADMIN));
        filter = new P1PlatformReadContextFilter(identity, permissions, properties, true);
        var tenants = mock(one.nexus.p1.port.TenantRepository.class);
        var registrations = mock(one.nexus.p1.port.TenantRegistrationRepository.class);
        var guard = new ThreadLocalIsolationGuard();
        var lifecycle = new TenantLifecycleService(tenants, null, null, null, guard, null, null, null);
        var registration = new TenantRegistrationService(registrations, lifecycle, guard, null, null, null);
        when(tenants.listAll()).thenAnswer(invocation -> {
            assertEquals(person, IsolationContextHolder.get().actor().subjectId());
            return List.of();
        });
        when(registrations.list(any())).thenReturn(List.of());
        mvc = MockMvcBuilders.standaloneSetup(new TenantController(lifecycle, null, null, null, properties),
                new TenantRegistrationController(registration))
                .addFilters(new DemoPlatformContextFilter(properties, new ObjectMapper()), filter).build();
    }

    void roles(Set<String> roles) {
        when(service.currentSubject()).thenReturn(new Subject(context.tenantId().toString(), person.toString(),
                roles, Set.of(), Set.of()));
        when(service.snapshot()).thenReturn(new PolicySnapshot(context.tenantId().toString(), 1));
    }

    @AfterEach void clean() {
        assertNull(TenantContextHolder.get());
        assertNull(IsolationContextHolder.get());
    }

    @ParameterizedTest @ValueSource(strings = {"/api/v1/p1/tenants", "/api/v1/p1/tenant-registrations"})
    void authenticPlatformListsWorkWithoutDemo(String path) throws Exception {
        mvc.perform(get(path).header("Authorization", "Bearer test-token")
                .header("X-Nexus-Context-Id", contextId)).andExpect(status().isOk());
        verify(service).check("tenant", "list");
        assertFalse(properties.getDemoContext().isEnabled());
    }

    @Test void demoHeadersCannotAuthorize() throws Exception {
        mvc.perform(get("/api/v1/p1/tenants").header("X-Nexus-Demo-Actor-Id", person)
                .header("X-Nexus-Demo-Reason", "spoof")).andExpect(status().isUnauthorized())
                .andExpect(reason("missing_credentials"));
        verifyNoInteractions(sessions, service);
    }

    @Test void ordinaryTenantEvenWithAdminRoleDenied() throws Exception {
        context = new TenantContext(contextId, UUID.randomUUID(), context.accountId(), person,
                "subject", context.issuedAt(), context.expiresAt());
        mvc.perform(get("/api/v1/p1/tenants").header("Authorization", "Bearer test-token")
                .header("X-Nexus-Context-Id", contextId)).andExpect(status().isForbidden())
                .andExpect(reason("not_platform_context"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"tenant_member", "platform_security_admin", "platform_audit_admin"})
    void otherRolesDenied(String role) throws Exception {
        roles(Set.of(role));
        mvc.perform(get("/api/v1/p1/tenant-registrations").header("Authorization", "Bearer test-token")
                .header("X-Nexus-Context-Id", contextId)).andExpect(status().isForbidden());
    }

    @Test void deniedPermissionFailsClosed() throws Exception {
        when(service.check("tenant", "list")).thenThrow(one.nexus.permission.support.PermissionException.denied("denied"));
        mvc.perform(get("/api/v1/p1/tenants").header("Authorization", "Bearer test-token")
                .header("X-Nexus-Context-Id", contextId)).andExpect(status().isForbidden())
                .andExpect(reason("permission_denied"));
    }

    @ParameterizedTest @ValueSource(strings = {"revoked", "expired", "not issued"})
    void invalidIssuedContextDenied(String reason) throws Exception {
        when(sessions.loadContext(eq(contextId), any(SessionService.AuthenticatedIdentity.class)))
                .thenThrow(one.nexus.p2.support.DomainException.unauthorized(reason));
        mvc.perform(get("/api/v1/p1/tenants").header("Authorization", "Bearer test-token")
                .header("X-Nexus-Context-Id", contextId)).andExpect(status().isUnauthorized())
                .andExpect(reason("session_invalid"));
    }

    @Test void clientTenantHeaderDenied() throws Exception {
        mvc.perform(get("/api/v1/p1/tenants").header("X-Tenant-Id", context.tenantId()))
                .andExpect(status().isBadRequest()).andExpect(reason("client_tenant_header"));
    }

    @Test void invalidTokenDenied() throws Exception {
        when(sessions.authenticate("test-token"))
                .thenThrow(one.nexus.p2.support.DomainException.unauthorized("invalid token"));
        mvc.perform(get("/api/v1/p1/tenants").header("Authorization", "Bearer test-token")
                .header("X-Nexus-Context-Id", contextId)).andExpect(status().isUnauthorized());
    }

    @Test void conflictingDutiesDenied() throws Exception {
        roles(Set.of(PlatformDutyCatalog.SYSTEM_ADMIN, PlatformDutyCatalog.AUDIT_ADMIN));
        mvc.perform(get("/api/v1/p1/tenants").header("Authorization", "Bearer test-token")
                .header("X-Nexus-Context-Id", contextId)).andExpect(status().isForbidden())
                .andExpect(reason("duty_set"));
    }

    @Test void scopeAndOffSwitchNeverBind() throws Exception {
        for (String method : List.of("POST", "PUT", "DELETE")) {
            assertTrue(filter.shouldNotFilter(new MockHttpServletRequest(method, "/api/v1/p1/tenants")));
        }
        assertTrue(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/v1/p1/tenants/123")));
        var disabled = new P1PlatformReadContextFilter(identity, permissions, properties, false);
        disabled.doFilter(new MockHttpServletRequest("GET", "/api/v1/p1/tenants"), new MockHttpServletResponse(),
                (req, res) -> assertNull(IsolationContextHolder.get()));
        verifyNoInteractions(sessions, service);
    }

    @Test void springConfigurationDefaultsOffAndExplicitlyEnables() {
        var runner = new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withBean(IdentityModule.class, () -> identity)
                .withBean(PermissionModule.class, () -> permissions)
                .withBean(P1Properties.class, () -> properties)
                .withUserConfiguration(P1PlatformReadContextFilter.class);
        var request = new MockHttpServletRequest("GET", "/api/v1/p1/tenants");
        runner.run(app -> assertTrue(app.getBean(P1PlatformReadContextFilter.class).shouldNotFilter(request)));
        runner.withPropertyValues("nexus.p1.platform-read-context.enabled=true", "spring.profiles.active=prod")
                .run(app -> assertFalse(app.getBean(P1PlatformReadContextFilter.class).shouldNotFilter(request)));
        assertFalse(properties.getDemoContext().isEnabled());
    }

    @Test void unavailablePermissionSnapshotDenied() throws Exception {
        when(service.snapshot()).thenThrow(new one.nexus.permission.engine.SnapshotUnavailableException("offline"));
        mvc.perform(get("/api/v1/p1/tenants").header("Authorization", "Bearer test-token")
                .header("X-Nexus-Context-Id", contextId)).andExpect(status().isForbidden())
                .andExpect(reason("snapshot_unavailable"));
    }

    @Test void mfaRefusalIsNamedAndTheBodyCarriesNoIdentifiers() throws Exception {
        when(sessions.loadContext(eq(contextId), any(SessionService.AuthenticatedIdentity.class)))
                .thenThrow(one.nexus.p2.support.DomainException.mfaRequired("admin needs mfa for " + person));
        var result = mvc.perform(get("/api/v1/p1/tenants").header("Authorization", "Bearer test-token")
                .header("X-Nexus-Context-Id", contextId)).andExpect(status().isForbidden())
                .andExpect(reason("mfa_required")).andReturn();
        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains(person.toString()));
        assertFalse(body.contains(contextId.toString()));
        assertFalse(body.contains("test-token"));
    }

    static org.springframework.test.web.servlet.ResultMatcher reason(String reason) {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.reason").value(reason);
    }

    @Test void exceptionInDownstreamStillCleans() {
        var request = new MockHttpServletRequest("GET", "/api/v1/p1/tenants");
        request.addHeader("Authorization", "Bearer test-token");
        request.addHeader("X-Nexus-Context-Id", contextId.toString());
        assertThrows(IllegalStateException.class, () -> filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> { throw new IllegalStateException("downstream"); }));
    }

    @Test void realIamIssuanceOwnershipAndRevocationAreEnforced() throws Exception {
        var idp = new one.nexus.p2.idp.StubIdentityProvider();
        var iam = new IdentityModule(new one.nexus.p2.persist.InMemoryIdentityRepository(), idp,
                new one.nexus.p2.smtp.KeycloakSmtpHook(idp, true), java.time.Clock.systemUTC(),
                java.time.Duration.ofMinutes(10));
        iam.adminInitializer().initialize(new one.nexus.p2.admin.TenantAdminInitializer.Command(
                properties.getPlatformTenant().getId(), "AUTH6 Test", "auth6@example.test", "auth6-sub"));
        String token = one.nexus.p2.idp.StubIdentityProvider.token("auth6-sub", "auth6@example.test", "auth6", "Test");
        var login = iam.sessions().loginWithAccessToken(token);
        assertNotNull(login.contextId());
        TenantContextHolder.clear();
        var realFilter = new P1PlatformReadContextFilter(iam, permissions, properties, true);
        var request = new MockHttpServletRequest("GET", "/api/v1/p1/tenants");
        request.addHeader("Authorization", "Bearer " + token);
        request.addHeader("X-Nexus-Context-Id", login.contextId().toString());
        realFilter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            var platform = new ThreadLocalIsolationGuard().requirePlatformContext("tenant.list", PlatformDuty.SYSTEM_ADMIN);
            assertEquals(login.personId(), platform.actor().subjectId());
        });
        String otherToken = one.nexus.p2.idp.StubIdentityProvider.token("other-sub", "other@example.test", "other", "Other");
        request.removeHeader("Authorization");
        request.addHeader("Authorization", "Bearer " + otherToken);
        var foreign = new MockHttpServletResponse();
        realFilter.doFilter(request, foreign, (req, res) -> fail("foreign context allowed"));
        assertEquals(403, foreign.getStatus());
        iam.repository().revokeContextsForAccount(login.accountId());
        request.removeHeader("Authorization");
        request.addHeader("Authorization", "Bearer " + token);
        var revoked = new MockHttpServletResponse();
        realFilter.doFilter(request, revoked, (req, res) -> fail("revoked context allowed"));
        assertEquals(401, revoked.getStatus());
        var stored = iam.repository().findContext(login.contextId()).orElseThrow();
        iam.repository().saveContext(new one.nexus.p2.domain.Model.IssuedContext(stored.id(), stored.tenantId(),
                stored.accountId(), stored.personId(), stored.idpSubject(), stored.issuedAt(),
                Instant.now().minusSeconds(1), null));
        var expired = new MockHttpServletResponse();
        realFilter.doFilter(request, expired, (req, res) -> fail("expired context allowed"));
        assertEquals(401, expired.getStatus());
    }
}
