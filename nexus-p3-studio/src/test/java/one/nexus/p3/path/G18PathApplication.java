package one.nexus.p3.path;

import java.time.Clock;
import javax.sql.DataSource;
import one.nexus.audit.AuditPort;
import one.nexus.audit.JdbcHashChainedAuditPort;
import one.nexus.crypto.CryptoPort;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import one.nexus.event.ExecutionSettings;
import one.nexus.event.OutboxPort;
import one.nexus.event.isolation.ExecutionIsolation;
import one.nexus.event.jdbc.JdbcOutboxStore;
import one.nexus.p3.P3StudioTestApplication;
import one.nexus.p3.flow.NexusP3FlowApplication;
import org.flywaydb.core.Flyway;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/** Test-only G18 path. Product boot is {@code one.nexus.bootstrap.NexusOneApplication}. */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = {
            "one.nexus.p3",
            "one.nexus.p2",
            "one.nexus.permission",
            "one.nexus.event",
            "one.nexus.notification"
        },
        excludeFilters = {
            @ComponentScan.Filter(type = FilterType.REGEX, pattern = "one\\.nexus\\.p3\\.flow\\.compat\\..*"),
            @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = {P3StudioTestApplication.class, NexusP3FlowApplication.class})
        })
public class G18PathApplication {
    public static void main(String[] args) {
        SpringApplication.run(G18PathApplication.class, args);
    }

    @Bean
    Flyway flyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas("platform")
                .defaultSchema("platform")
                .createSchemas(true)
                .load();
        flyway.migrate();
        return flyway;
    }

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(ExecutionIsolation.class)
    ExecutionIsolation executionIsolation() {
        return ExecutionIsolation.create();
    }

    @Bean
    @ConditionalOnMissingBean(ExecutionSettings.class)
    ExecutionSettings executionSettings() {
        return ExecutionSettings.defaults();
    }

    @Bean
    @ConditionalOnMissingBean(JdbcOutboxStore.class)
    JdbcOutboxStore jdbcOutboxStore(
            DataSource dataSource, ExecutionIsolation isolation, ExecutionSettings settings, Clock clock) {
        return new JdbcOutboxStore(dataSource, isolation, settings, clock);
    }

    @Bean
    @ConditionalOnMissingBean(OutboxPort.class)
    OutboxPort outboxPort(JdbcOutboxStore store) {
        return store;
    }

    @Bean
    CryptoPort cryptoPort() {
        return JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL);
    }

    @Bean
    AuditPort auditPort(DataSource dataSource, CryptoPort cryptoPort) {
        return new JdbcHashChainedAuditPort(dataSource, cryptoPort);
    }
}
