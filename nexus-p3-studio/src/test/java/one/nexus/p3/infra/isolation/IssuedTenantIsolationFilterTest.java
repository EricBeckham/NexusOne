package one.nexus.p3.infra.isolation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.p1.domain.Tenant;
import one.nexus.p1.domain.TenantKind;
import one.nexus.p1.domain.TenantStatus;
import one.nexus.p1.port.TenantRepository;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.idp.IdentityProviderPort;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.session.SessionService;
import one.nexus.p2.session.SessionService.AuthenticatedIdentity;
import one.nexus.p3.domain.LedgerDefinition;
import one.nexus.p3.domain.LedgerScope;
import one.nexus.p3.domain.LedgerStatus;
import one.nexus.p3.port.LedgerRepository;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class IssuedTenantIsolationFilterTest {
    private final IsolationKernel kernel =
            IsolationKernel.create(new TableRegistry(), Set.of("p3.schema"), new CollectingAuditSink());

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void handlesNotifyAndP3ButNotTodos() {
        assertTrue(IssuedTenantIsolationFilter.isHandled("/api/v1/notifications"));
        assertTrue(IssuedTenantIsolationFilter.isHandled("/api/v1/p3/ledgers"));
        assertTrue(IssuedTenantIsolationFilter.isHandled("/api/v1/p3/forms"));
        assertFalse(IssuedTenantIsolationFilter.isHandled("/api/v1/p3/todos"));
        assertFalse(IssuedTenantIsolationFilter.isHandled("/api/v1/persons"));
    }

    @Test
    void missingContextPassesThroughSoServicesReturn403() throws Exception {
        IssuedTenantIsolationFilter filter = filter(mockIdentity(), tenants(null), ledgers(null));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/notifications");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(200, response.getStatus());
        assertEquals(null, AccessContextHolder.currentOrNull());
    }

    @Test
    void clientTenantHeaderIsRejected() throws Exception {
        IssuedTenantIsolationFilter filter = filter(mockIdentity(), tenants(null), ledgers(null));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/notifications");
        request.addHeader("X-Tenant-Id", UUID.randomUUID().toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString().contains("P1.M02.009"));
    }

    @Test
    void issuedContextBindsIsolationKernel() throws Exception {
        UUID tenantId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID contextId = UUID.fromString("11111111-1111-4111-8111-111111111122");
        UUID personId = UUID.fromString("11111111-1111-4111-8111-111111111133");
        UUID accountId = UUID.fromString("11111111-1111-4111-8111-111111111144");
        IdentityModule identity = mockIdentity();
        SessionService sessions = identity.sessions();
        AuthenticatedIdentity auth = new AuthenticatedIdentity(
                null, new IdentityProviderPort.IdentityClaims("sub-a", "a@example.com", "a", "A", null));
        when(sessions.authenticate("stub.token")).thenReturn(auth);
        when(sessions.loadContext(eq(contextId), eq("sub-a"))).thenReturn(new TenantContext(
                contextId, tenantId, accountId, personId, "sub-a", Instant.now(), Instant.now().plusSeconds(3600)));
        TenantRepository repository = mock(TenantRepository.class);
        when(repository.findById(tenantId)).thenReturn(Optional.of(new Tenant(
                tenantId, "alpha", "Alpha", TenantKind.BUSINESS, TenantStatus.ENABLED, "Alpha Ltd",
                Instant.now(), personId, Instant.now(), personId, null, 1)));

        IssuedTenantIsolationFilter filter = filter(identity, tenants(repository), ledgers(null));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/p3/ledgers");
        request.addHeader("Authorization", "Bearer stub.token");
        request.addHeader("X-Nexus-Context-Id", contextId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, (req, res) -> {
            assertEquals(tenantId.toString(), AccessContextHolder.current().boundTenantId().orElseThrow());
            chain.doFilter(req, res);
        });
        assertEquals(null, AccessContextHolder.currentOrNull());
    }

    @Test
    void ledgerTenantCodeUsedWhenP1TenantMissing() throws Exception {
        UUID tenantId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID contextId = UUID.fromString("11111111-1111-4111-8111-111111111122");
        UUID personId = UUID.fromString("11111111-1111-4111-8111-111111111133");
        UUID accountId = UUID.fromString("11111111-1111-4111-8111-111111111144");
        IdentityModule identity = mockIdentity();
        SessionService sessions = identity.sessions();
        AuthenticatedIdentity auth = new AuthenticatedIdentity(
                null, new IdentityProviderPort.IdentityClaims("sub-a", "a@example.com", "a", "A", null));
        when(sessions.authenticate("stub.token")).thenReturn(auth);
        when(sessions.loadContext(eq(contextId), eq("sub-a"))).thenReturn(new TenantContext(
                contextId, tenantId, accountId, personId, "sub-a", Instant.now(), Instant.now().plusSeconds(3600)));
        LedgerRepository ledgerRepository = mock(LedgerRepository.class);
        when(ledgerRepository.list(tenantId)).thenReturn(List.of(new LedgerDefinition(
                UUID.fromString("11111111-1111-4111-8111-111111111155"),
                tenantId,
                "g18a",
                UUID.fromString("11111111-1111-4111-8111-111111111166"),
                "leave",
                "Leave",
                LedgerScope.TENANT,
                "l_g18a_leave",
                1,
                LedgerStatus.READY,
                Instant.now(),
                personId.toString(),
                Instant.now(),
                personId.toString(),
                null,
                1)));

        IssuedTenantIsolationFilter filter = filter(identity, tenants(null), ledgers(ledgerRepository));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/p3/ledgers/"
                + "11111111-1111-4111-8111-111111111155/records");
        request.addHeader("Authorization", "Bearer stub.token");
        request.addHeader("X-Nexus-Context-Id", contextId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            assertEquals(tenantId.toString(), AccessContextHolder.current().boundTenantId().orElseThrow());
            assertEquals("g18a", P3RequestContext.require().tenantCode());
        });
        assertEquals(null, AccessContextHolder.currentOrNull());
    }

    private static IdentityModule mockIdentity() {
        IdentityModule identity = mock(IdentityModule.class);
        SessionService sessions = mock(SessionService.class);
        when(identity.sessions()).thenReturn(sessions);
        return identity;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<TenantRepository> tenants(TenantRepository repository) {
        ObjectProvider<TenantRepository> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(repository);
        when(provider.getIfAvailable(any())).thenReturn(repository);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<LedgerRepository> ledgers(LedgerRepository repository) {
        ObjectProvider<LedgerRepository> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(repository);
        when(provider.getIfAvailable(any())).thenReturn(repository);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<TenantMemberGrantSeeder> noSeeder() {
        ObjectProvider<TenantMemberGrantSeeder> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }

    private IssuedTenantIsolationFilter filter(
            IdentityModule identity,
            ObjectProvider<TenantRepository> tenants,
            ObjectProvider<LedgerRepository> ledgers) {
        return new IssuedTenantIsolationFilter(kernel, identity, tenants, ledgers, noSeeder());
    }
}
