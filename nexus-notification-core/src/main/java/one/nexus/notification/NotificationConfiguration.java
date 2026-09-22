package one.nexus.notification;

import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
     * In-memory fallbacks for notify-only tests. {@code nexus.notify.outbox=auto}
     * (product / G18) must use M0-09 {@link OutboxPort} — never steal that bean
     * before DataSource auto-config registers {@code JdbcOutboxStore}.
     */
    @Bean
    @ConditionalOnMissingBean(OutboxPort.class)
    @ConditionalOnProperty(name = "nexus.notify.outbox", havingValue = "record")
    OutboxPort recordingOutboxPort() {
        return new RecordingOutboxPort();
    }

    @Bean
    @ConditionalOnMissingBean(OutboxPort.class)
    @ConditionalOnProperty(name = "nexus.notify.outbox", havingValue = "immediate")
    OutboxPort immediateOutboxPort(InAppDeliveryHandler handler) {
        return new ImmediateOutboxPort(List.of(handler));
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
