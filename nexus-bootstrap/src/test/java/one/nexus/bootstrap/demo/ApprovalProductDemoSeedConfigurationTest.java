package one.nexus.bootstrap.demo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.DeploymentBuilder;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class ApprovalProductDemoSeedConfigurationTest {
    private final ApprovalProductDemoSeedConfiguration configuration = new ApprovalProductDemoSeedConfiguration();

    @Test
    void isInertWhenDemoSwitchIsOff() throws Exception {
        var env = new MockEnvironment().withProperty(DemoProfileGate.ENABLED, "false");
        @SuppressWarnings("unchecked")
        ObjectProvider<RepositoryService> processes = mock(ObjectProvider.class);
        assertDoesNotThrow(() -> configuration.approvalProductDemoSeed(null, env, null, processes)
                .run((ApplicationArguments) null));
        verifyNoInteractions(processes);
    }

    @Test
    void refusesRemoteDatabaseBeforeAnyWrite() {
        var jdbc = mock(JdbcClient.class);
        var transaction = mock(TransactionTemplate.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RepositoryService> processes = mock(ObjectProvider.class);
        var env = new MockEnvironment().withProperty(DemoProfileGate.ENABLED, "true")
                .withProperty(DemoProfileGate.ENVIRONMENT, "local")
                .withProperty("spring.datasource.url", "jdbc:postgresql://db.example.test/nexusone");
        assertThrows(IllegalStateException.class,
                () -> configuration.approvalProductDemoSeed(jdbc, env, transaction, processes)
                        .run((ApplicationArguments) null));
        verifyNoInteractions(jdbc, transaction, processes);
    }

    @Test
    void seedsPublishedTemplateProcessAndCatalogWithoutSubscribing() throws Exception {
        var jdbc = mock(JdbcClient.class);
        var statement = mock(JdbcClient.StatementSpec.class);
        var transaction = mock(TransactionTemplate.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RepositoryService> processes = mock(ObjectProvider.class);
        when(jdbc.sql(org.mockito.ArgumentMatchers.anyString())).thenReturn(statement);
        when(statement.param(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(statement);
        when(statement.update()).thenReturn(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transaction).executeWithoutResult(org.mockito.ArgumentMatchers.any());
        var env = new MockEnvironment().withProperty(DemoProfileGate.ENABLED, "true")
                .withProperty(DemoProfileGate.ENVIRONMENT, "local")
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/nexusone");

        configuration.approvalProductDemoSeed(jdbc, env, transaction, processes).run((ApplicationArguments) null);

        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.atLeastOnce()).sql(sql.capture());
        String joined = String.join("\n", sql.getAllValues());
        assertTrue(joined.contains("演示数据"));
        assertTrue(joined.contains("'PUBLISHED'"));
        assertTrue(joined.contains("'PERSON'"));
        var bound = org.mockito.ArgumentCaptor.forClass(Object.class);
        org.mockito.Mockito.verify(statement, org.mockito.Mockito.atLeastOnce())
                .param(org.mockito.ArgumentMatchers.anyString(), bound.capture());
        assertTrue(bound.getAllValues().contains(ApprovalProductDemoSeedConfiguration.PROCESS_KEY));
        assertTrue(bound.getAllValues().stream().anyMatch(value -> String.valueOf(value).contains("<userTask ")));
        for (String query : sql.getAllValues()) {
            assertTrue(query.contains("ON CONFLICT") || query.contains("running_version_id IS NULL"));
            assertTrue(!query.contains("market.subscription") && !query.contains("market.installation"),
                    "demo rows must not grant a tenant subscription or installation");
        }
        verify(processes).getIfAvailable();
    }

    @Test
    void publishesOneLevelProcessOnceUsingM10DeploymentShape() {
        RepositoryService repository = mock(RepositoryService.class);
        ProcessDefinitionQuery query = mock(ProcessDefinitionQuery.class);
        DeploymentBuilder deployment = mock(DeploymentBuilder.class);
        when(repository.createProcessDefinitionQuery()).thenReturn(query);
        when(query.processDefinitionKey(ApprovalProductDemoSeedConfiguration.PROCESS_KEY)).thenReturn(query);
        when(query.processDefinitionTenantId(P1DemoSeedConfiguration.TENANT_ID.toString())).thenReturn(query);
        when(query.latestVersion()).thenReturn(query);
        when(query.singleResult()).thenReturn(null);
        when(repository.createDeployment()).thenReturn(deployment);
        when(deployment.name(org.mockito.ArgumentMatchers.anyString())).thenReturn(deployment);
        when(deployment.tenantId(P1DemoSeedConfiguration.TENANT_ID.toString())).thenReturn(deployment);
        when(deployment.addString(org.mockito.ArgumentMatchers.eq("demoSimpleApproval.bpmn20.xml"),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(deployment);

        ApprovalProductDemoSeedConfiguration.publishProcess(repository);

        var xml = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(deployment).addString(org.mockito.ArgumentMatchers.eq("demoSimpleApproval.bpmn20.xml"), xml.capture());
        assertEquals(1, xml.getValue().split("<userTask ", -1).length - 1);
        assertTrue(xml.getValue().contains("演示数据"));
        verify(deployment).deploy();

        when(query.singleResult()).thenReturn(mock(ProcessDefinition.class));
        ApprovalProductDemoSeedConfiguration.publishProcess(repository);
        verify(repository, org.mockito.Mockito.times(1)).createDeployment();
        ApprovalProductDemoSeedConfiguration.publishProcess(null);
    }
}
