package one.nexus.notification;

import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import one.nexus.event.IdempotencyStore;
import one.nexus.event.OutboxPort;
import one.nexus.notification.infra.InMemoryNotificationRepository;
import one.nexus.notification.infra.JdbcNotificationRepository;

@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfiguration {
    @Bean
    @ConditionalOnMissingBean
    TemplateCatalog templateCatalog() {
        return new TemplateCatalog();
    }

    @Bean
    @ConditionalOnMissingBean
    NotificationRepository notificationRepository(
            NotificationProperties properties, ObjectProvider<JdbcClient> jdbc) {
        if ("jdbc".equalsIgnoreCase(properties.getPersistence()) && jdbc.getIfAvailable() != null) {
            return new JdbcNotificationRepository(jdbc.getObject());
        }
        return new InMemoryNotificationRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    InAppDeliveryHandler inAppDeliveryHandler(
            NotificationRepository repository, ObjectProvider<IdempotencyStore> idempotency) {
        return new InAppDeliveryHandler(repository, idempotency.getIfAvailable());
    }

    /**
     * Fallback only when M0-09 {@code EventCoreConfiguration} has no DataSource.
     * The monolith uses {@code JdbcOutboxStore} as {@link OutboxPort}.
     */
    @Bean
    @ConditionalOnMissingBean(OutboxPort.class)
    OutboxPort outboxPort(NotificationProperties properties, InAppDeliveryHandler handler) {
        if ("immediate".equalsIgnoreCase(properties.getOutbox())) {
            return new ImmediateOutboxPort(List.of(handler));
        }
        return new RecordingOutboxPort();
    }

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock notifyClock() {
        return Clock.systemUTC();
    }

    @Bean
    NotificationService notificationService(
            TemplateCatalog templates,
            NotificationRepository repository,
            OutboxPort outbox,
            Clock clock) {
        return new NotificationService(templates, repository, outbox, clock);
    }

    @Bean
    ApprovalNotificationPublisher approvalNotificationPublisher(NotificationService notifications) {
        return new ApprovalNotificationPublisher(notifications);
    }
}
