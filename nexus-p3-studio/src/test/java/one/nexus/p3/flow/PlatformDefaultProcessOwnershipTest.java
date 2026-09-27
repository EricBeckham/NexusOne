package one.nexus.p3.flow;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import one.nexus.p1.config.P1Properties;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.DeploymentQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class PlatformDefaultProcessOwnershipTest {
    @Test void defaultLookupPinsConfiguredOwnerAndVersionRatherThanAnyTenantOrLatest() {
        RepositoryService repository = mock(RepositoryService.class);
        ProcessDefinitionQuery query = mock(ProcessDefinitionQuery.class, RETURNS_SELF);
        DeploymentQuery deployments = mock(DeploymentQuery.class, RETURNS_SELF);
        ProcessDefinition definition = mock(ProcessDefinition.class);
        UUID platform = UUID.randomUUID();
        P1Properties properties = new P1Properties(); properties.getPlatformTenant().setId(platform);
        when(repository.createProcessDefinitionQuery()).thenReturn(query);
        when(repository.createDeploymentQuery()).thenReturn(deployments);
        when(query.singleResult()).thenReturn(definition);
        when(definition.getKey()).thenReturn("default-expense");
        when(definition.getTenantId()).thenReturn(platform.toString());
        when(definition.getVersion()).thenReturn(2);
        when(definition.getDeploymentId()).thenReturn("deployment-2");
        var service = new ProcessDefinitionPublishService(repository, mock(ProcessDefinitionDraftService.class),
                mock(ProcessDefinitionValidationService.class), mock(ObjectProvider.class));
        service.setPlatformProperties(properties);
        assertEquals(platform.toString(), service.getPlatformDefault("default-expense", 2).tenantId());
        verify(query).processDefinitionTenantId(platform.toString()); verify(query).processDefinitionVersion(2);
        verify(query, never()).latestVersion(); verify(query, never()).list();
    }

    @Test void absentPlatformVersionFailsRatherThanCrossTenantFallback() {
        RepositoryService repository = mock(RepositoryService.class);
        ProcessDefinitionQuery query = mock(ProcessDefinitionQuery.class, RETURNS_SELF);
        when(repository.createProcessDefinitionQuery()).thenReturn(query);
        var service = new ProcessDefinitionPublishService(repository, mock(ProcessDefinitionDraftService.class),
                mock(ProcessDefinitionValidationService.class), mock(ObjectProvider.class));
        service.setPlatformProperties(new P1Properties());
        assertThrows(FlowException.class, () -> service.getPlatformDefault("same-key", 1));
        verify(query, never()).list();
    }
}
