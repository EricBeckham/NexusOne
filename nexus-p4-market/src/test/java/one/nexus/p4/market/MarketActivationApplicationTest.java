package one.nexus.p4.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import one.nexus.common.error.NexusException;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.p1.domain.Tenant;
import one.nexus.p1.port.AuditPort;
import one.nexus.p1.port.TenantRepository;
import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.domain.Model;
import one.nexus.p4.market.MarketModels.*;
import org.junit.jupiter.api.Test;

class MarketActivationApplicationTest {
    @org.junit.jupiter.api.AfterEach void clearPlatform() { PlatformActivationTestContext.clear(); }
    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

    @Test
    void tenantAdminRequestsPendingActivationWithoutInstall() {
        UUID tenantId = UUID.randomUUID();
        UUID personId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID formId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        MarketRepository market = mock(MarketRepository.class);
        TenantRepository tenants = mock(TenantRepository.class);
        IdentityRepository iam = mock(IdentityRepository.class);
        AuditPort audit = mock(AuditPort.class);
        ProductDefaultPermissionProvisioner packs = mock(ProductDefaultPermissionProvisioner.class);
        MarketService service = new MarketService(market, tenants, Clock.fixed(NOW, ZoneOffset.UTC), iam, audit, packs);

        stubTenantAdmin(iam, tenantId, personId, positionId);
        when(market.productDefinitionForSku("HR_BASIC")).thenReturn(Optional.of(
                new ProductDefinition(productId, "hr", "HR", "", formId, null, null, Map.of(), "PUBLISHED", NOW, NOW, 1)));
        when(market.sku("HR_BASIC")).thenReturn(Optional.of(
                new Sku("HR_BASIC", appId, "HR Basic", Map.of("apps", new EntitlementSpec(true, 5, Map.of())))));
        when(market.version(versionId)).thenReturn(Optional.of(
                new AppVersion(versionId, appId, "1.0", VersionStatus.PUBLISHED, "", NOW, NOW)));
        when(market.hasActiveSubscription(tenantId, "HR_BASIC")).thenReturn(false);
        when(market.activationApplicationsForCurrentTenant()).thenReturn(List.of());

        var kernel = IsolationKernel.create(new TableRegistry().freeze(), Set.of(), new CollectingAuditSink());
        var context = kernel.tenants().issue(
                new AuthenticatedMembership(Actor.person(personId.toString()), tenantId.toString(), "m", Set.of(tenantId.toString())),
                "trace-act");

        AccessContextHolder.call(context, () -> {
            ActivationApplication application = service.requestActivation(tenantId, "HR_BASIC", versionId);
            assertThat(application.status()).isEqualTo(ActivationApplicationStatus.PENDING);
            assertThat(application.skuCode()).isEqualTo("HR_BASIC");
            verify(market).saveActivationApplication(any());
            verify(market, never()).saveInstallation(any());
            verify(packs, never()).provision(any(), any());
            return null;
        });
    }

    @Test
    void platformApproveActivatesInstallAndPins() {
        UUID tenantId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID formId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        MarketRepository market = mock(MarketRepository.class);
        TenantRepository tenants = mock(TenantRepository.class);
        ProductDefaultPermissionProvisioner packs = mock(ProductDefaultPermissionProvisioner.class);
        AuditPort audit = mock(AuditPort.class);
        MarketService service = new MarketService(market, tenants, Clock.fixed(NOW, ZoneOffset.UTC), null, audit, packs);

        ActivationApplication pending = new ActivationApplication(
                applicationId, tenantId, "HR_BASIC", versionId,
                ActivationApplicationStatus.PENDING, null, null, null, null, null, NOW, actor);
        when(market.activationApplication(applicationId)).thenReturn(Optional.of(pending));
        when(market.hasActiveSubscription(tenantId, "HR_BASIC")).thenReturn(false);
        when(tenants.findById(tenantId)).thenReturn(Optional.of(mock(Tenant.class)));
        when(tenants.findProjection(tenantId)).thenReturn(Optional.empty());
        when(market.sku("HR_BASIC")).thenReturn(Optional.of(
                new Sku("HR_BASIC", appId, "HR Basic", Map.of("apps", new EntitlementSpec(true, 5, Map.of())))));
        when(market.version(versionId)).thenReturn(Optional.of(
                new AppVersion(versionId, appId, "1.0", VersionStatus.PUBLISHED, "", NOW, NOW)));
        when(market.productDefinitionForSku("HR_BASIC")).thenReturn(Optional.of(
                new ProductDefinition(productId, "hr", "HR", "", formId, null, null, Map.of(), "PUBLISHED", NOW, NOW, 1)));

        PlatformActivationTestContext.bind();
        when(market.activationApplicationForReview(applicationId)).thenReturn(Optional.of(pending));
        ActivationApplication approved = service.approveActivation(applicationId, "通过");
        assertThat(approved.status()).isEqualTo(ActivationApplicationStatus.APPROVED);
        assertThat(approved.resultingInstallationId()).isNotNull();
        verify(market).saveApprovedSubscription(any(),any());
        verify(market).saveApprovedInstallation(any(),any());
        verify(market).saveApprovedProductPin(any(),any());
        verify(market,never()).saveInstallation(any());
        verify(packs).provisionApproved(any(),any());
        verify(audit).append(any());
    }

    @Test
    void rejectRequiresNote() {
        PlatformActivationTestContext.bind();
        UUID applicationId = UUID.randomUUID();
        MarketRepository market = mock(MarketRepository.class);
        MarketService service = new MarketService(market, mock(TenantRepository.class), Clock.fixed(NOW, ZoneOffset.UTC));
        when(market.activationApplicationForReview(applicationId)).thenReturn(Optional.of(new ActivationApplication(
                applicationId, UUID.randomUUID(), "SKU", UUID.randomUUID(),
                ActivationApplicationStatus.PENDING, null, null, null, null, null, NOW, UUID.randomUUID())));
        assertThatThrownBy(() -> service.rejectActivation(applicationId, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reject note");
    }

    private static void stubTenantAdmin(IdentityRepository iam, UUID tenantId, UUID personId, UUID positionId) {
        var person = new Model.Person(personId, tenantId, UUID.randomUUID(), "Admin", "A-1", "a@example.com", null, null,
                Model.PersonStatus.ACTIVE, NOW, NOW, null, 1);
        var employment = new Model.Employment(UUID.randomUUID(), tenantId, personId, UUID.randomUUID(), positionId,
                Model.EmploymentKind.PRIMARY, Model.EmploymentStatus.ACTIVE, null, null, NOW, NOW, null, 1);
        var position = new Model.Position(positionId, tenantId, "TENANT_ADMIN", "Tenant admin", "admin", null,
                Model.OrgStatus.ACTIVE, NOW, NOW, null, 1);
        when(iam.findPerson(tenantId, personId)).thenReturn(Optional.of(person));
        when(iam.listEmploymentsByPerson(tenantId, personId)).thenReturn(List.of(employment));
        when(iam.findPosition(tenantId, positionId)).thenReturn(Optional.of(position));
    }
}
