package one.nexus.event;

import java.time.Clock;
import java.util.Set;
import javax.sql.DataSource;
import one.nexus.event.isolation.ExecutionIsolation;
import one.nexus.event.isolation.Slf4jIsolationAuditSink;
import one.nexus.event.jdbc.JdbcDeadLetterStore;
import one.nexus.event.jdbc.JdbcIdempotencyStore;
import one.nexus.event.jdbc.JdbcJobLogWriter;
import one.nexus.event.jdbc.JdbcOutboxStore;
import one.nexus.event.jdbc.JdbcReconciliation;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.IsolationAuditSink;
import one.nexus.isolation.query.TableRegistry;
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
    IsolationKernel isolationKernel(ObjectProvider<IsolationAuditSink> sinks) {
        return IsolationKernel.create(
                new TableRegistry(),
                Set.of(
                        ExecutionIsolation.RECONCILE_PATH,
                        ExecutionIsolation.QUARTZ_PATH,
                        ExecutionIsolation.OUTBOX_PATH,
                        "files.reconcile",
                        "notify.ops",
                        "cache.ops",
                        "p3.schema",
                        "p3.studio",
                        "flow.ops"),
                sinks.getIfAvailable(Slf4jIsolationAuditSink::new));
    }

    @Bean
    @ConditionalOnMissingBean
    ExecutionIsolation executionIsolation(IsolationKernel kernel) {
        return new ExecutionIsolation(kernel);
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
            ObjectProvider<Clock> clocks
    ) {
        return new JdbcOutboxStore(dataSource, isolation, settings, clock(clocks));
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
    IdempotencyStore idempotencyStore(DataSource dataSource, ObjectProvider<Clock> clocks) {
        return new JdbcIdempotencyStore(dataSource, clock(clocks));
    }

    @Bean
    @ConditionalOnMissingBean
    JdbcDeadLetterStore jdbcDeadLetterStore(DataSource dataSource, ObjectProvider<Clock> clocks) {
        return new JdbcDeadLetterStore(dataSource, clock(clocks));
    }

    @Bean
    @ConditionalOnMissingBean(DeadLetterStore.class)
    DeadLetterStore deadLetterStore(JdbcDeadLetterStore store) {
        return store;
    }

    @Bean
    @ConditionalOnMissingBean
    JobLogWriter jobLogWriter(DataSource dataSource, ObjectProvider<Clock> clocks) {
        return new JdbcJobLogWriter(dataSource, clock(clocks));
    }

    @Bean
    @ConditionalOnMissingBean
    ReconciliationPort reconciliationPort(
            JdbcOutboxStore outbox,
            DataSource dataSource,
            JdbcDeadLetterStore deadLetters,
            ObjectProvider<Clock> clocks
    ) {
        return new JdbcReconciliation(outbox, dataSource, deadLetters, clock(clocks));
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
            ObjectProvider<Clock> clocks,
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
                clock(clocks),
                properties.dispatcherEnabled());
    }

    /**
     * Product already has more than one {@link Clock} ({@code clock} from IAM,
     * {@code studioClock} when scan order creates it first). Injecting {@code Clock}
     * by type would fail after Outbox auto-config attaches.
     */
    private static Clock clock(ObjectProvider<Clock> clocks) {
        return clocks.orderedStream().findFirst().orElseGet(Clock::systemUTC);
    }
}
