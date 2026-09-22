package one.nexus.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {
    void insert(Notification notification);

    void update(Notification notification);

    Optional<Notification> find(UUID id);

    List<Notification> listByRecipient(UUID tenantId, String recipientSubject);
}
