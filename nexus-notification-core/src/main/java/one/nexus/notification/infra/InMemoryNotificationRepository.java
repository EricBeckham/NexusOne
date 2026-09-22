package one.nexus.notification.infra;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import one.nexus.notification.Notification;
import one.nexus.notification.NotificationRepository;

public final class InMemoryNotificationRepository implements NotificationRepository {
    private final Map<UUID, Notification> rows = new ConcurrentHashMap<>();

    @Override
    public void insert(Notification notification) {
        rows.put(notification.id(), notification);
    }

    @Override
    public void update(Notification notification) {
        rows.put(notification.id(), notification);
    }

    @Override
    public Optional<Notification> find(UUID id) {
        return Optional.ofNullable(rows.get(id));
    }

    @Override
    public List<Notification> listByRecipient(UUID tenantId, String recipientSubject) {
        return rows.values().stream()
                .filter(item -> item.tenantId().equals(tenantId) && item.recipientSubject().equals(recipientSubject))
                .sorted(Comparator.comparing(Notification::createdAt).reversed())
                .toList();
    }
}
