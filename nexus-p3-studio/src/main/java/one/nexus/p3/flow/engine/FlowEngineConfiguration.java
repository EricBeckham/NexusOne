package one.nexus.p3.flow.engine;

import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.spring.ProcessEngineFactoryBean;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * In-process Flowable 7.1.0 on the business DataSource / transaction manager.
 * Engine tables live in schema {@code flowable} and are created by Flowable itself.
 */
@Configuration
@EnableTransactionManagement
@ConditionalOnProperty(name = "nexus.flow.engine")
public class FlowEngineConfiguration {
    public static final String PROCESS_KEY = "nexusSequentialApproval";
    public static final String SCHEMA = "flowable";

    @Bean
    SpringProcessEngineConfiguration springProcessEngineConfiguration(
            DataSource dataSource,
            PlatformTransactionManager transactionManager
    ) {
        createSchema(dataSource);
        SpringProcessEngineConfiguration configuration = new SpringProcessEngineConfiguration();
        configuration.setDataSource(dataSource);
        configuration.setTransactionManager(transactionManager);
        configuration.setDatabaseSchema(SCHEMA);
        configuration.setDatabaseSchemaUpdate(SpringProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        configuration.setAsyncExecutorActivate(false);
        configuration.setDisableIdmEngine(true);
        // Shared BPMN for every tenant. Isolation is business_meta.tenant_id, not Flowable tenantId.
        configuration.setFallbackToDefaultTenant(true);
        configuration.setDeploymentResources(new Resource[]{
                new ClassPathResource("processes/nexus-sequential-approval.bpmn20.xml")
        });
        configuration.setDeploymentName("nexus-m0-08-sequential");
        return configuration;
    }

    @Bean
    ProcessEngineFactoryBean processEngineFactory(SpringProcessEngineConfiguration configuration) {
        ProcessEngineFactoryBean factory = new ProcessEngineFactoryBean();
        factory.setProcessEngineConfiguration(configuration);
        return factory;
    }

    @Bean
    RuntimeService flowableRuntimeService(ProcessEngine processEngine) {
        return processEngine.getRuntimeService();
    }

    @Bean
    TaskService flowableTaskService(ProcessEngine processEngine) {
        return processEngine.getTaskService();
    }

    @Bean
    RepositoryService flowableRepositoryService(ProcessEngine processEngine) {
        return processEngine.getRepositoryService();
    }

    @Bean
    HistoryService flowableHistoryService(ProcessEngine processEngine) {
        return processEngine.getHistoryService();
    }

    static void createSchema(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS flowable");
        } catch (Exception ex) {
            throw new IllegalStateException("failed to create flowable schema", ex);
        }
    }
}
