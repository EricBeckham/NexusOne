package one.nexus.event;

import java.time.Clock;
import javax.sql.DataSource;
import one.nexus.event.isolation.ExecutionIsolation;
import one.nexus.event.jdbc.JdbcDeadLetterStore;
import one.nexus.event.jdbc.JdbcIdempotencyStore;
import one.nexus.event.jdbc.JdbcJobLogWriter;
import one.nexus.event.jdbc.JdbcOutboxStore;
import one.nexus.event.jdbc.JdbcReconciliation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * M0-09 JDBC outbox / dispatcher. Must be <strong>auto-configuration</strong>, not a
 * component-scanned {@code @Configuration}: {@code @ConditionalOnBean(DataSource)} on a
 * scanned class is evaluated before {@link DataSourceAutoConfiguration}, so the product
 * monolith never registered {@link JdbcOutboxStore} / {@link OutboxPort}. G18 IT hid this
 * by declaring those beans on {@code G18PathApplication}.
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnBean(DataSource.class)
@EnableConfigurationProperties(ExecutionProperties.class)
public class EventCoreConfiguration {
    @Bean
    @ConditionalOnMissingBean
    ExecutionIsolation executionIsolation() {
        return ExecutionIsolation.create();
    }

    @Bean
    @ConditionalOnMissingBean
    Clock executionClock() {
        return Clock.systemUTC();
    }

    @Bean
    ExecutionSettings executionSettings(ExecutionProperties properties) {
        return properties.toSettings();
    }

    @Bean
    @ConditionalOnMissingBean
    JdbcOutboxStore jdbcOutboxStore(
            DataSource dataSource,
            ExecutionIsolation isolation,
            ExecutionSettings settings,
            Clock executionClock
    ) {
        return new JdbcOutboxStore(dataSource, isolation, settings, executionClock);
    }

    @Bean
    @ConditionalOnMissingBean(OutboxPort.class)
    OutboxPort outboxPort(JdbcOutboxStore store) {
        return store;
    }

    @Bean
    @ConditionalOnMissingBean(EventPort.class)
    EventPort eventPort(JdbcOutboxStore store) {
        return store;
    }

    @Bean
    @ConditionalOnMissingBean
    IdempotencyStore idempotencyStore(DataSource dataSource, Clock executionClock) {
        return new JdbcIdempotencyStore(dataSource, executionClock);
    }

    @Bean
    @ConditionalOnMissingBean
    JdbcDeadLetterStore jdbcDeadLetterStore(DataSource dataSource, Clock executionClock) {
        return new JdbcDeadLetterStore(dataSource, executionClock);
    }

    @Bean
    @ConditionalOnMissingBean(DeadLetterStore.class)
    DeadLetterStore deadLetterStore(JdbcDeadLetterStore store) {
        return store;
    }

    @Bean
    @ConditionalOnMissingBean
    JobLogWriter jobLogWriter(DataSource dataSource, Clock executionClock) {
        return new JdbcJobLogWriter(dataSource, executionClock);
    }

    @Bean
    @ConditionalOnMissingBean
    ReconciliationPort reconciliationPort(
            JdbcOutboxStore outbox,
            DataSource dataSource,
            JdbcDeadLetterStore deadLetters,
            Clock executionClock
    ) {
        return new JdbcReconciliation(outbox, dataSource, deadLetters, executionClock);
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxDispatcher outboxDispatcher(
            JdbcOutboxStore store,
            ExecutionIsolation isolation,
            ObjectProvider<OutboxHandler> handlers,
            IdempotencyStore idempotency,
            DeadLetterStore deadLetters,
            JobLogWriter jobLog,
            ExecutionSettings settings,
            Clock executionClock,
            ExecutionProperties properties
    ) {
        return new OutboxDispatcher(
                store,
                isolation,
                handlers.orderedStream().toList(),
                idempotency,
                deadLetters,
                jobLog,
                settings,
                executionClock,
                properties.dispatcherEnabled());
    }
}
