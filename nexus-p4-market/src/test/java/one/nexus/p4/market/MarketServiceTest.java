package one.nexus.p4.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import one.nexus.p1.domain.EntitlementPayload;
import one.nexus.p1.domain.EntitlementProjection;
import one.nexus.p1.domain.ProjectionSource;
import one.nexus.p1.domain.Tenant;
import one.nexus.p1.port.TenantRepository;
import one.nexus.p4.market.MarketModels.*;

class MarketServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-24T00:00:00Z");

    @Test
    void publishesInstallsAndProjectsSubscriptionEntitlements() {
        MarketRepository market = mock(MarketRepository.class);
        TenantRepository tenants = mock(TenantRepository.class);
        MarketService service = new MarketService(market, tenants, Clock.fixed(NOW, ZoneOffset.UTC));

        AppPackage app = service.createApp("leave", "请假", "请假应用");
        when(market.app(app.id())).thenReturn(Optional.of(app));
        AppVersion draft = service.createVersion(app.id(), "1.0.0", "首发");
        when(market.version(draft.id())).thenReturn(Optional.of(draft));
        AppVersion review = service.transition(draft.id(), VersionStatus.IN_REVIEW);
        when(market.version(draft.id())).thenReturn(Optional.of(review));
        AppVersion approved = service.transition(draft.id(), VersionStatus.APPROVED);
        when(market.version(draft.id())).thenReturn(Optional.of(approved));
        AppVersion published = service.transition(draft.id(), VersionStatus.PUBLISHED);
        when(market.version(draft.id())).thenReturn(Optional.of(published));

        Sku sku = service.defineSku("LEAVE_TEAM", app.id(), "团队版",
                Map.of("apps", new EntitlementSpec(true, 10, Map.of("tier", "team"))));
        when(market.sku(sku.code())).thenReturn(Optional.of(sku));
        UUID tenantId = UUID.randomUUID();
        when(tenants.findById(tenantId)).thenReturn(Optional.of(mock(Tenant.class)));
        when(tenants.findProjection(tenantId)).thenReturn(Optional.of(
                EntitlementProjection.fromDefaultPack(tenantId, "DEFAULT", "1",
                        EntitlementPayload.defaultPack(1, 1), NOW)));

        IsolationKernel isolation = IsolationKernel.create(
                new TableRegistry().freeze(), Set.of(), new CollectingAuditSink());
        var context = isolation.tenants().issue(
                new AuthenticatedMembership(
                        Actor.person("market-test-person"),
                        tenantId.toString(),
                        tenantId.toString(),
                        Set.of(tenantId.toString())),
                "market-test-trace");
        AccessContextHolder.call(context, () -> {
            Installation installation = service.subscribeAndInstall(tenantId, sku.code(), published.id());

            assertThat(installation.status()).isEqualTo(InstallationStatus.INSTALLED);
            ArgumentCaptor<EntitlementProjection> projection = ArgumentCaptor.forClass(EntitlementProjection.class);
            verify(tenants).updateProjection(projection.capture());
            assertThat(projection.getValue().source()).isEqualTo(ProjectionSource.SUBSCRIPTION);
            assertThat(projection.getValue().skuCode()).isEqualTo("LEAVE_TEAM");
            assertThat(projection.getValue().packCode()).isEqualTo("LEAVE_TEAM");
            assertThat(projection.getValue().packVersion()).isEqualTo("1");
            assertThat(projection.getValue().payload().entitlements().get("apps").quota()).isEqualTo(10);
            assertThat(projection.getValue().version()).isEqualTo(2);
            verify(market).saveInstallation(any());
            return null;
        });
    }
}
