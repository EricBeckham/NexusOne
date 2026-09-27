package one.nexus.p4.market;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import one.nexus.p1.domain.Actor;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.infra.isolation.IsolationContextHolder;
import one.nexus.p1.port.IsolationGuard.IsolationSnapshot;
import one.nexus.p1.port.PlatformContext;
import one.nexus.p1.port.AuditPort;
import one.nexus.p2.domain.IdentityRepository;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import org.junit.jupiter.api.*;
class PlatformActivationCommandTest {
    @AfterEach void clear(){PlatformActivationTestContext.clear();}
    static MarketModels.ActivationApplication pending(UUID tenant){return new MarketModels.ActivationApplication(UUID.randomUUID(),tenant,"sku",UUID.randomUUID(),MarketModels.ActivationApplicationStatus.PENDING,null,null,null,null,null,Instant.now(),UUID.randomUUID());}
    static MarketModels.ProductDefinition definition(){return new MarketModels.ProductDefinition(UUID.randomUUID(),"product","Product","",UUID.randomUUID(),"flow",1,Map.of("malicious","platform_system_admin"),"DRAFT",Instant.now(),Instant.now(),1);}
    @Test void capabilityCannotOutliveRequestOrTargetAnotherTenant(){
        PlatformActivationTestContext.bind();var command=PlatformActivationCommand.forPending(pending(UUID.randomUUID()),definition());
        assertThrows(SecurityException.class,()->command.assertTenant(UUID.randomUUID()));
        PlatformActivationTestContext.clear();assertThrows(RuntimeException.class,command::assertActive);
    }
    @Test void noOperatorCannotEvenLockAnApplication(){
        var market=mock(MarketRepository.class);var service=new MarketService(market,mock(one.nexus.p1.port.TenantRepository.class),java.time.Clock.systemUTC());
        assertThrows(RuntimeException.class,()->service.approveActivation(UUID.randomUUID(),"yes"));verifyNoInteractions(market);
    }
    @Test void demoDutyDoesNotMintCapability(){
        PlatformActivationTestContext.bind();var before=IsolationContextHolder.get();
        var actor=new Actor(before.actorId(),before.actor().homeTenantId(),"demo",Set.of(PlatformDuty.ALL_DEMO));
        IsolationContextHolder.set(new IsolationSnapshot(actor,new PlatformContext(true,"authenticated platform content operation",actor,"trace",actor.homeTenantId()),null,"trace"));
        assertThrows(RuntimeException.class,()->PlatformActivationCommand.forPending(pending(UUID.randomUUID()),definition()));
    }
    @Test void mismatchedActorAndChangedProductCannotMintOrUseCapability(){
        PlatformActivationTestContext.bind();var before=IsolationContextHolder.get();
        var command=PlatformActivationCommand.forPending(pending(UUID.randomUUID()),definition());
        assertThrows(SecurityException.class,()->command.assertDefinition(definition()));
        var actor=new Actor(UUID.randomUUID(),before.actor().homeTenantId(),"Other",Set.of(PlatformDuty.SYSTEM_ADMIN));
        IsolationContextHolder.set(new IsolationSnapshot(actor,new PlatformContext(true,"authenticated platform content operation",actor,"trace",actor.homeTenantId()),null,"trace"));
        assertThrows(RuntimeException.class,()->PlatformActivationCommand.forPending(pending(UUID.randomUUID()),definition()));
        assertThrows(RuntimeException.class,command::assertActive);
    }
    @Test void approvedPackIsLimitedToTargetRuntimeRoleAndIdempotent(){
        PlatformActivationTestContext.bind(); UUID tenant=UUID.randomUUID();var definition=definition();var command=PlatformActivationCommand.forPending(pending(tenant),definition);
        var identities=mock(IdentityRepository.class);
        var now=Instant.now();var member=new one.nexus.p2.domain.Model.Person(UUID.randomUUID(),tenant,UUID.randomUUID(),"Member","test",null,null,null,one.nexus.p2.domain.Model.PersonStatus.ACTIVE,now,now,null,1);
        when(identities.listPersons(tenant)).thenReturn(List.of(member));
        var policies=new InMemoryPolicyRepository();var module=PermissionModule.create(policies,identities,new InMemoryPolicyInvalidation());
        var pack=new ProductDefaultPermissionProvisioner(module,identities,mock(AuditPort.class));
        pack.provisionApproved(command,definition);pack.provisionApproved(command,definition);
        assertEquals(1,policies.listRoles(tenant).size()); assertEquals(6,policies.listGrants(tenant).size());
        assertEquals(1,policies.listAssignments(tenant).size());
        assertEquals(member.id().toString(),policies.listAssignments(tenant).getFirst().principalId());
        assertTrue(policies.listGrants(tenant).stream().noneMatch(grant->grant.rule().resource().startsWith("permission") || grant.rule().resource().startsWith("tenant")));
        assertTrue(policies.listRoles(one.nexus.p2.kernel.TenantContextHolder.require().tenantId()).isEmpty());
    }
}
