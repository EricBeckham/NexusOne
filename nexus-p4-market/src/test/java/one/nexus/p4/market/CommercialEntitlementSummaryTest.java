package one.nexus.p4.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
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
import one.nexus.p1.domain.EntitlementPayload;
import one.nexus.p1.domain.EntitlementProjection;
import one.nexus.p1.port.TenantRepository;
import one.nexus.p4.market.CommercialEntitlementService.CommercialEntitlement;
import one.nexus.p4.market.CommercialEntitlementService.EntitlementSummary;
import one.nexus.p4.market.MarketModels.EntitlementSpec;
import one.nexus.p4.market.MarketModels.Sku;
import one.nexus.p4.market.MarketModels.Subscription;
import one.nexus.p4.market.MarketModels.SubscriptionStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** XB10: the per-subscription summary reuses M20 lines and the P1 tenant projection — true / false / unknown. */
class CommercialEntitlementSummaryTest {
    private static final UUID TENANT = UUID.fromString("01900000-0000-7000-8000-0000000000c1");
    private static final Instant NOW = Instant.parse("2026-09-26T00:00:00Z");
    private final MarketRepository market = mock(MarketRepository.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final UUID latest = UUID.randomUUID();
    private final UUID older = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        IsolationKernel kernel = IsolationKernel.create(new TableRegistry().freeze(), Set.of(), new CollectingAuditSink());
        AccessContextHolder.set(kernel.tenants().issue(new AuthenticatedMembership(
                Actor.person("xb10"), TENANT.toString(), TENANT.toString(), Set.of(TENANT.toString())), "xb10-trace"));
        Sku sku = new Sku("TEAM", UUID.randomUUID(), "团队版", Map.of(
                "apps", new EntitlementSpec(true, 10, Map.of()),
                "reports", new EntitlementSpec(true, null, Map.of()),
                "api", new EntitlementSpec(false, null, Map.of())));
        when(market.sku("TEAM")).thenReturn(Optional.of(sku));
        when(market.installations()).thenReturn(List.of());
        Subscription first = new Subscription(older, TENANT, "TEAM", SubscriptionStatus.ACTIVE, NOW.minusSeconds(60), null);
        Subscription second = new Subscription(latest, TENANT, "TEAM", SubscriptionStatus.ACTIVE, NOW, null);
        when(market.subscriptions()).thenReturn(List.of(first, second));
        when(market.subscriptionForCurrentTenant(latest)).thenReturn(Optional.of(second));
    }

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    private static EntitlementProjection subscriptionProjection(UUID subscriptionId) {
        return EntitlementProjection.fromSubscription(TENANT, "TEAM", subscriptionId,
                new EntitlementPayload(EntitlementPayload.SCHEMA_V1, Map.of()), NOW, 3);
    }

    private EntitlementSummary summaryOf(List<CommercialEntitlement> views, UUID subscriptionId) {
        return views.stream().filter(view -> view.subscriptionId().equals(subscriptionId)).findFirst().orElseThrow().summary();
    }

    @Test
    void onlyTheSubscriptionThePlatformProjectionPointsAtIsProjected() {
        when(tenants.findProjection(TENANT)).thenReturn(Optional.of(subscriptionProjection(latest)));
        List<CommercialEntitlement> views = new CommercialEntitlementService(market, tenants).list();

        EntitlementSummary current = summaryOf(views, latest);
        assertEquals(2, current.enabledCount());
        assertEquals(3, current.totalCount());
        assertEquals(Boolean.TRUE, current.projected());
        assertEquals(3L, current.projectionVersion());
        assertEquals(NOW, current.projectedAt());

        EntitlementSummary earlier = summaryOf(views, older);
        assertEquals(Boolean.FALSE, earlier.projected());
        assertNull(earlier.projectionVersion());
        assertNull(earlier.projectedAt());
    }

    @Test
    void noProjectionOrADefaultPackMeansNotProjected() {
        when(tenants.findProjection(TENANT)).thenReturn(Optional.empty());
        assertEquals(Boolean.FALSE, summaryOf(new CommercialEntitlementService(market, tenants).list(), latest).projected());

        when(tenants.findProjection(TENANT)).thenReturn(Optional.of(EntitlementProjection.fromDefaultPack(
                TENANT, "default", "1", new EntitlementPayload(EntitlementPayload.SCHEMA_V1, Map.of()), NOW)));
        CommercialEntitlement detail = new CommercialEntitlementService(market, tenants).get(latest);
        assertEquals(Boolean.FALSE, detail.summary().projected());
    }

    @Test
    void anUnreadableProjectionIsUnknownNotFalse() {
        when(tenants.findProjection(TENANT)).thenThrow(new IllegalStateException("platform schema unavailable"));
        EntitlementSummary summary = summaryOf(new CommercialEntitlementService(market, tenants).list(), latest);
        assertNull(summary.projected(), "unknown must stay null, never guessed");
        assertEquals(2, summary.enabledCount());

        EntitlementSummary withoutRepository = summaryOf(new CommercialEntitlementService(market).list(), latest);
        assertNull(withoutRepository.projected());
        assertFalse(withoutRepository.totalCount() == 0);
        assertTrue(withoutRepository.enabledCount() <= withoutRepository.totalCount());
    }
}