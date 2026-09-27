package one.nexus.p4.market;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.UUID;
import java.util.Optional;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.flow.ProcessDefinitionPublishService;
import one.nexus.p3.infra.permission.ProcessDefinitionSurfaceGuard;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
class InstallationProcessConfigurationServiceTest {
    MarketService market=mock(MarketService.class);
    InstallationProcessConfigurationRepository repository=mock(InstallationProcessConfigurationRepository.class);
    ProcessDefinitionPublishService processes=mock(ProcessDefinitionPublishService.class);
    ProcessDefinitionSurfaceGuard guard=mock(ProcessDefinitionSurfaceGuard.class);
    InstallationProcessConfigurationService service=new InstallationProcessConfigurationService(market,repository,processes,guard,mock(ObjectProvider.class));
    UUID tenant=UUID.randomUUID(),installation=UUID.randomUUID(),person=UUID.randomUUID();
    @BeforeEach void context() {
        TenantContextHolder.set(new TenantContext(UUID.randomUUID(),tenant,UUID.randomUUID(),person,"subject",Instant.now(),Instant.now().plusSeconds(60)));
        when(market.getInstallation(installation)).thenReturn(new MarketModels.Installation(installation,tenant,UUID.randomUUID(),UUID.randomUUID(),MarketModels.InstallationStatus.INSTALLED,Instant.now(),Instant.now()));
        when(market.installationProductPin(installation)).thenReturn(new MarketModels.InstallationProductPin(installation,UUID.randomUUID(),UUID.randomUUID(),"default",2,Instant.now()));
        when(repository.find(tenant,installation)).thenReturn(Optional.empty());
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void savesOnlyTenantPublishedVersionAndLeavesDefaultPinUntouched() {
        service.save(installation,"custom",3);
        verify(processes).get("custom",3); verify(repository).save(eq(tenant),eq(installation),eq("custom"),eq(3),eq(person),any());
        verify(market,never()).subscribeProduct(any(),any(),any());
    }
    @Test void permissionDenialPreventsOwnershipQueriesAndMutation() {
        doThrow(new SecurityException("denied")).when(guard).requireWrite();
        assertThrows(SecurityException.class,()->service.save(installation,"custom",3));
        verifyNoInteractions(repository,processes); verify(market,never()).getInstallation(any());
    }
    @Test void foreignInstallationIsRejectedBeforePublishedLookupOrWrite() {
        when(market.getInstallation(installation)).thenThrow(new SecurityException("foreign"));
        assertThrows(SecurityException.class,()->service.save(installation,"custom",3)); verifyNoInteractions(repository,processes);
    }
    @Test void missingTenantVersionCannotSilentlyUseDefault() {
        when(processes.get("custom",3)).thenThrow(new IllegalStateException("missing tenant version"));
        assertThrows(IllegalStateException.class,()->service.save(installation,"custom",3));
        verify(repository,never()).save(any(),any(),any(),anyInt(),any(),any());
    }
    @Test void resetDeletesOnlyCurrentTenantsOverride() {
        var config=service.reset(installation); assertEquals("default",config.defaultKey());
        verify(repository).delete(tenant,installation); verifyNoInteractions(processes);
    }
}
