package one.nexus.p3.flow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.UUID;
import one.nexus.p1.config.P1Properties;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;

/** Explicit opt-in IT: supply a disposable loopback test_* PostgreSQL URL; never uses app datasource defaults. */
class ProductDefaultEngineContractIT {
    static ProcessEngine engine;
    static final UUID PLATFORM=UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID TENANT=UUID.fromString("00000000-0000-0000-0000-000000000011");
    static ProcessDefinitionPublishService publish;
    @BeforeAll static void start() {
        String url=System.getProperty("nexus.test.product.jdbc","");
        assertTrue(url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/test_[a-z_]+"),"explicit disposable loopback test database required");
        assertFalse(url.contains(":5432/"),"existing default port forbidden");
        engine=ProcessEngineConfiguration.createStandaloneProcessEngineConfiguration()
                .setJdbcDriver("org.postgresql.Driver").setJdbcUrl(url).setJdbcUsername("postgres").setJdbcPassword("")
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE).setAsyncExecutorActivate(false)
                .buildProcessEngine();
        deploy(PLATFORM,"Default v1"); deploy(PLATFORM,"Default v2"); deploy(TENANT,"Tenant v1");
        publish=new ProcessDefinitionPublishService(engine.getRepositoryService(),mock(ProcessDefinitionDraftService.class),
                mock(ProcessDefinitionValidationService.class),mock(ObjectProvider.class));
        var properties=new P1Properties();properties.getPlatformTenant().setId(PLATFORM);publish.setPlatformProperties(properties);
    }
    static void deploy(UUID owner,String name) {
        String xml="""
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="http://nexus.test">
                  <process id="expense" name="%s" isExecutable="true">
                    <startEvent id="start"/><sequenceFlow id="a" sourceRef="start" targetRef="approve"/>
                    <userTask id="approve" name="Approve" flowable:assignee="${assignee}"/>
                    <sequenceFlow id="b" sourceRef="approve" targetRef="end"/><endEvent id="end"/>
                  </process>
                </definitions>
                """.formatted(name);
        engine.getRepositoryService().createDeployment().tenantId(owner.toString()).addString("expense.bpmn20.xml",xml).deploy();
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @AfterAll static void close() { if(engine!=null)engine.close(); }
    @Test void sharedDefaultUsesExactPlatformVersionButInstanceBelongsToRequestingTenant() {
        var defaultPin=publish.getPlatformDefault("expense",1);
        var instance=engine.getRuntimeService().createProcessInstanceBuilder().processDefinitionId(defaultPin.processDefinitionId())
                .overrideProcessDefinitionTenantId(TENANT.toString()).businessKey("product-default-contract").variable("assignee","reviewer").start();
        assertEquals(TENANT.toString(),instance.getTenantId());
        assertEquals(defaultPin.processDefinitionId(),instance.getProcessDefinitionId());
        var task=engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult();
        assertEquals("reviewer",task.getAssignee());
        engine.getTaskService().complete(task.getId());
        assertNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(instance.getId()).singleResult());
    }
    @Test void sameKeyAndVersionInTenantCannotReplaceDefaultOrChangeAnExistingInstance() {
        var defaultPin=publish.getPlatformDefault("expense",1);
        var running=engine.getRuntimeService().createProcessInstanceBuilder().processDefinitionId(defaultPin.processDefinitionId())
                .overrideProcessDefinitionTenantId(TENANT.toString()).businessKey("existing-default").variable("assignee","reviewer").start();
        TenantContextHolder.set(new TenantContext(UUID.randomUUID(),TENANT,UUID.randomUUID(),UUID.randomUUID(),"test",Instant.now(),Instant.now().plusSeconds(60)));
        var customized=publish.get("expense",1);
        assertEquals(TENANT.toString(),customized.tenantId()); assertNotEquals(defaultPin.processDefinitionId(),customized.processDefinitionId());
        assertEquals(defaultPin.processDefinitionId(),engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(running.getId()).singleResult().getProcessDefinitionId());
        assertEquals(2,publish.getPlatformDefault("expense",2).version());
        assertThrows(FlowException.class,()->publish.getPlatformDefault("expense",3));
    }
}
