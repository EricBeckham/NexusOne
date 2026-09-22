package one.nexus.notification.infra;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import one.nexus.notification.Notification;
import one.nexus.notification.NotificationRepository;
import one.nexus.notification.NotificationStatus;

public final class JdbcNotificationRepository implements NotificationRepository {
    private final JdbcClient jdbc;

    public JdbcNotificationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Notification notification) {
        jdbc.sql("""
                        INSERT INTO notify.inbox (
                          notification_id, tenant_id, recipient_subject, template_code, title, body,
                          payload_json, status, outbox_message_id, created_at, read_at)
                        VALUES (
                          :id, :tenant_id, :recipient, :template, :title, :body,
                          :payload, :status, :outbox_id, :created_at, :read_at)
                        """)
                .param("id", notification.id())
                .param("tenant_id", notification.tenantId())
                .param("recipient", notification.recipientSubject())
                .param("template", notification.templateCode())
                .param("title", notification.title())
                .param("body", notification.body())
                .param("payload", notification.payloadJson())
                .param("status", notification.status().name())
                .param("outbox_id", notification.outboxMessageId())
                .param("created_at", notification.createdAt())
                .param("read_at", notification.readAt())
                .update();
    }

    @Override
    public void update(Notification notification) {
        jdbc.sql("""
                        UPDATE notify.inbox
                           SET status = :status,
                               outbox_message_id = :outbox_id,
                               read_at = :read_at
                         WHERE notification_id = :id
                        """)
                .param("status", notification.status().name())
                .param("outbox_id", notification.outboxMessageId())
                .param("read_at", notification.readAt() == null ? null : Timestamp.from(notification.readAt()))
                .param("id", notification.id())
                .update();
    }

    @Override
    public Optional<Notification> find(UUID id) {
        return jdbc.sql("""
                        SELECT notification_id, tenant_id, recipient_subject, template_code, title, body,
                               payload_json, status, outbox_message_id, created_at, read_at
                          FROM notify.inbox
                         WHERE notification_id = :id
                        """)
                .param("id", id)
                .query(this::map)
                .optional();
    }

    @Override
    public List<Notification> listByRecipient(UUID tenantId, String recipientSubject) {
        return jdbc.sql("""
                        SELECT notification_id, tenant_id, recipient_subject, template_code, title, body,
                               payload_json, status, outbox_message_id, created_at, read_at
                          FROM notify.inbox
                         WHERE tenant_id = :tenant_id AND recipient_subject = :recipient
                         ORDER BY created_at DESC
                        """)
                .param("tenant_id", tenantId)
                .param("recipient", recipientSubject)
                .query(this::map)
                .list();
    }

    private Notification map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        Timestamp readAt = rs.getTimestamp("read_at");
        return new Notification(
                rs.getObject("notification_id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("recipient_subject"),
                rs.getString("template_code"),
                rs.getString("title"),
                rs.getString("body"),
                rs.getString("payload_json"),
                NotificationStatus.valueOf(rs.getString("status")),
                rs.getObject("outbox_message_id", UUID.class),
                rs.getTimestamp("created_at").toInstant(),
                readAt == null ? null : readAt.toInstant());
    }
}
