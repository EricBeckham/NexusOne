package one.nexus.p3.studio;

import java.time.Clock;
import java.util.Set;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.p3.port.ApplicationRepository;
import one.nexus.p3.port.FieldRepository;
import one.nexus.p3.port.LedgerRepository;
import one.nexus.p3.studio.form.FormService;
import one.nexus.p3.studio.form.FormStore;
import one.nexus.p3.studio.form.InMemoryFormStore;
import one.nexus.p3.studio.form.JdbcFormStore;
import one.nexus.p3.studio.form.PublishHook;
import one.nexus.p3.studio.meta.InMemoryLedgerMetadataCatalog;
import one.nexus.p3.studio.meta.JdbcLedgerMetadataPort;
import one.nexus.p3.studio.meta.LedgerMetadataPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(StudioProperties.class)
public class StudioConfiguration {
    @Bean
    @ConditionalOnMissingBean
    IsolationKernel studioIsolationKernel() {
        return IsolationKernel.create(
                new TableRegistry(), Set.of("p3.studio"), new CollectingAuditSink());
    }

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock studioClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(LedgerMetadataPort.class)
    LedgerMetadataPort ledgerMetadataPort(
            StudioProperties properties,
            ObjectProvider<LedgerRepository> ledgers,
            ObjectProvider<FieldRepository> fields,
            ObjectProvider<ApplicationRepository> applications) {
        LedgerRepository ledgerRepo = ledgers.getIfAvailable();
        FieldRepository fieldRepo = fields.getIfAvailable();
        if (ledgerRepo != null && fieldRepo != null) {
            return new JdbcLedgerMetadataPort(ledgerRepo, fieldRepo, applications.getIfAvailable());
        }
        InMemoryLedgerMetadataCatalog catalog = new InMemoryLedgerMetadataCatalog();
        if (properties.isSeedDemoMetadata()) {
            catalog.seedDemoLeaveRequest();
        }
        return catalog;
    }

    @Bean
    @ConditionalOnMissingBean(PublishHook.class)
    PublishHook publishHook() {
        return PublishHook.noop();
    }

    @Bean
    @ConditionalOnMissingBean(FormStore.class)
    FormStore formStore(
            StudioProperties properties, ObjectProvider<JdbcClient> jdbc, ObjectProvider<ObjectMapper> mapper) {
        if ("jdbc".equalsIgnoreCase(properties.getPersistence()) && jdbc.getIfAvailable() != null) {
            ObjectMapper objectMapper = mapper.getIfAvailable(ObjectMapper::new);
            return new JdbcFormStore(jdbc.getObject(), objectMapper);
        }
        return new InMemoryFormStore();
    }

    @Bean
    FormService formService(
            FormStore store,
            LedgerMetadataPort metadata,
            PublishHook hook,
            Clock clock,
            ObjectProvider<PlatformTransactionManager> transactions) {
        return new FormService(store, metadata, hook, clock, transactions.getIfAvailable());
    }
}
