package one.nexus.p4.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import org.springframework.web.server.ResponseStatusException;

class MarketSelfSubscribeTest {
    private static final Instant NOW=Instant.parse("2026-09-25T00:00:00Z");

    @Test void tenantAdminInstallsAndPinsTheBoundVersions() {
        UUID tenantId=UUID.randomUUID(), personId=UUID.randomUUID(), appId=UUID.randomUUID(), versionId=UUID.randomUUID();
        UUID formId=UUID.randomUUID(), productId=UUID.randomUUID(), positionId=UUID.randomUUID();
        MarketRepository market=mock(MarketRepository.class); TenantRepository tenants=mock(TenantRepository.class);
        IdentityRepository iam=mock(IdentityRepository.class); AuditPort audit=mock(AuditPort.class);
        ProductDefaultPermissionProvisioner permissionPacks=mock(ProductDefaultPermissionProvisioner.class);
        MarketService service=new MarketService(market,tenants,Clock.fixed(NOW,ZoneOffset.UTC),iam,audit,permissionPacks);
        var kernel=IsolationKernel.create(new TableRegistry().freeze(),Set.of(),new CollectingAuditSink());
        var context=kernel.tenants().issue(new AuthenticatedMembership(Actor.person(personId.toString()),tenantId.toString(),"member-a",Set.of(tenantId.toString())),"trace-a");
        var person=new Model.Person(personId,tenantId,UUID.randomUUID(),"Admin","A-1","a@example.com",null,null,
                Model.PersonStatus.ACTIVE,NOW,NOW,null,1);
        var employment=new Model.Employment(UUID.randomUUID(),tenantId,personId,UUID.randomUUID(),positionId,
                Model.EmploymentKind.PRIMARY,Model.EmploymentStatus.ACTIVE,null,null,NOW,NOW,null,1);
        assertThat(MarketService.isTenantAdminPosition("demo_tenant_admin")).isTrue();
        assertThat(MarketService.isTenantAdminPosition("TENANT_ADMIN")).isTrue();
        var position=new Model.Position(positionId,tenantId,"TENANT_ADMIN","Tenant admin","admin",null,
                Model.OrgStatus.ACTIVE,NOW,NOW,null,1);
        when(iam.findPerson(tenantId,personId)).thenReturn(Optional.of(person));
        when(iam.listEmploymentsByPerson(tenantId,personId)).thenReturn(java.util.List.of(employment));
        when(iam.findPosition(tenantId,positionId)).thenReturn(Optional.of(position));
        when(tenants.findById(tenantId)).thenReturn(Optional.of(mock(Tenant.class)));
        when(tenants.findProjection(tenantId)).thenReturn(Optional.empty());
        when(market.subscriptions()).thenReturn(java.util.List.of());
        Sku sku=new Sku("HR_BASIC",appId,"HR Basic",Map.of("apps",new EntitlementSpec(true,5,Map.of())));
        when(market.sku("HR_BASIC")).thenReturn(Optional.of(sku));
        when(market.version(versionId)).thenReturn(Optional.of(new AppVersion(versionId,appId,"1.0",VersionStatus.PUBLISHED,"",NOW,NOW)));
        when(market.productDefinitionForSku("HR_BASIC")).thenReturn(Optional.of(new ProductDefinition(productId,"hr","HR","",formId,null,null,Map.of(),"DRAFT",NOW,NOW,1)));

        AccessContextHolder.call(context,()->{
            Installation installation=service.subscribeProduct(tenantId,"HR_BASIC",versionId);
            assertThat(installation.status()).isEqualTo(InstallationStatus.INSTALLED);
            verify(market).saveInstallationProductPin(new InstallationProductPin(installation.id(),productId,formId,null,null,NOW));
            verify(permissionPacks).provision(tenantId, new ProductDefinition(productId,"hr","HR","",formId,null,null,Map.of(),"DRAFT",NOW,NOW,1));
            verify(audit).append(any());
            return null;
        });
    }

    @Test void nonAdminAndCrossTenantAreDeniedBeforeSubscriptionWrites() {
        UUID tenantId=UUID.randomUUID(), otherTenant=UUID.randomUUID(), actor=UUID.randomUUID();
        MarketRepository market=mock(MarketRepository.class); TenantRepository tenants=mock(TenantRepository.class);
        IdentityRepository iam=mock(IdentityRepository.class); MarketService service=new MarketService(market,tenants,
                Clock.fixed(NOW,ZoneOffset.UTC),iam,mock(AuditPort.class),mock(ProductDefaultPermissionProvisioner.class));
        var kernel=IsolationKernel.create(new TableRegistry().freeze(),Set.of(),new CollectingAuditSink());
        var context=kernel.tenants().issue(new AuthenticatedMembership(Actor.person(actor.toString()),tenantId.toString(),"member",Set.of(tenantId.toString())),"trace");
        when(iam.findPerson(tenantId,actor)).thenReturn(Optional.empty());
        AccessContextHolder.call(context,()->{
            NexusException denied = catchThrowableOfType(
                    () -> service.subscribeProduct(tenantId, "SKU", UUID.randomUUID()), NexusException.class);
            assertThat(denied.httpStatus()).isEqualTo(403);
            assertThat(denied.errorCode().code()).isEqualTo("P4.MARKET.0002");
            assertThat(denied).hasMessage("tenant administrator role is required");
            assertThatThrownBy(()->service.subscribeProduct(otherTenant,"SKU",UUID.randomUUID())).isInstanceOf(ResponseStatusException.class);
            return null;
        });
        verify(market,never()).saveSubscription(any());
    }
}
