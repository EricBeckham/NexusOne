package one.nexus.p4.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p1.port.AuditPort;
import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.domain.Model;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.engine.DataScopeKind;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ProductDefaultPermissionProvisionerTest {
    @AfterEach void clearContext() { TenantContextHolder.clear(); }

    @Test void provisionsOnlyCurrentTenantMembersWithStartAndAssigneeCapabilities() {
        UUID tenant=UUID.randomUUID(), otherTenant=UUID.randomUUID(), personId=UUID.randomUUID(), account=UUID.randomUUID();
        UUID otherPersonId=UUID.randomUUID();
        IdentityRepository identity=mock(IdentityRepository.class);
        Model.Person person=new Model.Person(personId,tenant,account,"Member","M-1","m@example.com",null,null,
                Model.PersonStatus.ACTIVE,Instant.EPOCH,Instant.EPOCH,null,1);
        when(identity.listPersons(tenant)).thenReturn(List.of(person));
        when(identity.findPerson(tenant,personId)).thenReturn(Optional.of(person));
        when(identity.findPerson(otherTenant,personId)).thenReturn(Optional.empty());
        when(identity.listEmploymentsByPerson(tenant,personId)).thenReturn(List.of());
        Model.Person otherPerson=new Model.Person(otherPersonId,otherTenant,UUID.randomUUID(),"Other","O-1","o@example.com",null,null,
                Model.PersonStatus.ACTIVE,Instant.EPOCH,Instant.EPOCH,null,1);
        when(identity.findPerson(otherTenant,otherPersonId)).thenReturn(Optional.of(otherPerson));
        when(identity.listEmploymentsByPerson(otherTenant,otherPersonId)).thenReturn(List.of());
        var policies=new InMemoryPolicyRepository();
        var module=PermissionModule.create(policies,identity,new InMemoryPolicyInvalidation());
        AuditPort audit=mock(AuditPort.class);
        var provisioner=new ProductDefaultPermissionProvisioner(module,identity,audit);
        var definition=new MarketModels.ProductDefinition(UUID.randomUUID(),"travel-basic","Travel","",
                UUID.randomUUID(),"travelApproval",3,Map.of(),"DRAFT",Instant.EPOCH,Instant.EPOCH,1);
        TenantContextHolder.set(new TenantContext(UUID.randomUUID(),tenant,account,personId,"person",Instant.now(),Instant.now().plusSeconds(60)));

        assertThat(provisioner.provision(tenant,definition)).isEqualTo(1);
        String roleCode=ProductDefaultPermissionProvisioner.roleCode(definition.id());
        var role=policies.findRoleByCode(tenant,roleCode).orElseThrow();
        assertThat(policies.listAssignments(tenant)).anyMatch(item -> personId.toString().equals(item.principalId())
                && role.id().equals(item.roleId()));
        assertThat(policies.listGrants(tenant)).anyMatch(grant -> grant.rule()!=null
                && roleCode.equals(grant.rule().principalId()) && "process.instance".equals(grant.rule().resource())
                && "start".equals(grant.rule().action()) && DataScopeKind.ALL==grant.rule().scopeKind());
        assertThat(policies.listAssignments(otherTenant)).isEmpty();
        assertThat(policies.listGrants(otherTenant)).isEmpty();
        assertThatThrownBy(()->provisioner.requireTenantMember(otherTenant,personId)).isInstanceOf(SecurityException.class);
        assertThat(module.service().decide("process.instance","start").allowed()).isTrue();
        TenantContextHolder.set(new TenantContext(UUID.randomUUID(),otherTenant,otherPerson.accountId(),otherPersonId,
                "other",Instant.now(),Instant.now().plusSeconds(60)));
        assertThat(module.service().decide("process.instance","start").allowed()).isFalse();
        verify(audit).append(argThat(event -> "p4.product_permission_pack.provisioned".equals(event.eventType())
                && tenant.equals(event.tenantId())));
    }
}
