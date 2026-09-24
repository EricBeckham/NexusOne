package one.nexus.p4.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.p4.market.CommercialEntitlementService.CommercialEntitlement;
import one.nexus.p4.market.MarketModels.EntitlementSpec;
import one.nexus.p4.market.MarketModels.Installation;
import one.nexus.p4.market.MarketModels.InstallationStatus;
import one.nexus.p4.market.MarketModels.Sku;
import one.nexus.p4.market.MarketModels.Subscription;
import one.nexus.p4.market.MarketModels.SubscriptionStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class CommercialEntitlementServiceTest {
    private static final UUID TENANT_A = UUID.fromString("01900000-0000-7000-8000-0000000000a1");
    private static final UUID TENANT_B = UUID.fromString("01900000-0000-7000-8000-0000000000b1");
    private static final Instant NOW = Instant.parse("2026-09-25T00:00:00Z");

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void projectsSkuLinesAndInstallationWithoutPrices() throws Exception {
        UUID appId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID installationId = UUID.randomUUID();
        Sku sku = new Sku("LEAVE_TEAM", appId, "团队版", Map.of(
                "apps", new EntitlementSpec(true, 10, Map.of("price", 999))));
        Subscription own = new Subscription(subscriptionId, TENANT_A, "LEAVE_TEAM", SubscriptionStatus.ACTIVE, NOW, null);
        Subscription foreign = new Subscription(UUID.randomUUID(), TENANT_B, "LEAVE_TEAM", SubscriptionStatus.ACTIVE, NOW, null);
        Installation installation = new Installation(
                installationId, TENANT_A, appId, UUID.randomUUID(), InstallationStatus.INSTALLED, NOW, NOW);
        MarketRepository market = mock(MarketRepository.class);
        when(market.subscriptions()).thenReturn(List.of(foreign, own));
        when(market.installations()).thenReturn(List.of(installation));
        when(market.sku("LEAVE_TEAM")).thenReturn(Optional.of(sku));
        when(market.subscriptionForCurrentTenant(subscriptionId)).thenReturn(Optional.of(own));
        when(market.subscriptionForCurrentTenant(foreign.id())).thenReturn(Optional.empty());
        CommercialEntitlementService service = new CommercialEntitlementService(market);
        use(TENANT_A);

        List<CommercialEntitlement> listed = service.list();
        assertEquals(List.of(subscriptionId), listed.stream().map(CommercialEntitlement::subscriptionId).toList());
        CommercialEntitlement detail = service.get(subscriptionId);
        assertEquals("团队版", detail.skuName());
        assertEquals(installationId, detail.installationId());
        assertEquals("INSTALLED", detail.installationStatus());
        assertEquals(10, detail.lines().getFirst().quota());
        assertTrue(detail.lines().getFirst().enabled());

        String json = new ObjectMapper().writeValueAsString(detail);
        assertFalse(json.contains("999"));
        assertFalse(json.contains("price"));
        assertFalse(json.contains("amount"));
    }

    @Test
    void missingSkuAndEmptyTenantStayHonest() {
        UUID subscriptionId = UUID.randomUUID();
        Subscription own = new Subscription(subscriptionId, TENANT_A, "MISSING", SubscriptionStatus.CANCELLED, NOW, NOW);
        MarketRepository market = mock(MarketRepository.class);
        when(market.subscriptions()).thenReturn(List.of(own));
        when(market.installations()).thenReturn(List.of());
        when(market.sku("MISSING")).thenReturn(Optional.empty());
        when(market.subscriptionForCurrentTenant(subscriptionId)).thenReturn(Optional.of(own));
        CommercialEntitlementService service = new CommercialEntitlementService(market);
        use(TENANT_A);

        CommercialEntitlement detail = service.get(subscriptionId);
        assertNull(detail.skuName());
        assertNull(detail.appId());
        assertNull(detail.installationId());
        assertTrue(detail.lines().isEmpty());

        use(TENANT_B);
        when(market.subscriptions()).thenReturn(List.of());
        assertTrue(service.list().isEmpty());
        ResponseStatusException missing = assertThrows(ResponseStatusException.class, () -> service.get(subscriptionId));
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
    }

    @Test
    void missingTenantContextIsForbiddenAndViewHasNoMoneyFields() {
        CommercialEntitlementService service = new CommercialEntitlementService(mock(MarketRepository.class));
        ResponseStatusException denied = assertThrows(ResponseStatusException.class, service::list);
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());

        List<String> names = new java.util.ArrayList<>();
        for (RecordComponent component : CommercialEntitlement.class.getRecordComponents()) {
            names.add(component.getName().toLowerCase(Locale.ROOT));
        }
        for (RecordComponent component : CommercialEntitlementService.EntitlementLine.class.getRecordComponents()) {
            names.add(component.getName().toLowerCase(Locale.ROOT));
        }
        for (String forbidden : List.of("price", "amount", "fee", "invoice", "coupon")) {
            assertFalse(names.contains(forbidden), forbidden);
        }
    }

    private static void use(UUID tenantId) {
        IsolationKernel isolation = IsolationKernel.create(new TableRegistry().freeze(), Set.of(), new CollectingAuditSink());
        AccessContextHolder.set(isolation.tenants().issue(
                new AuthenticatedMembership(Actor.person("m20-reader"), tenantId.toString(), tenantId.toString(), Set.of(tenantId.toString())),
                "m20-trace"));
    }
}
