package one.nexus.notification;

import java.util.Map;

/** M0-08 calls this when an approval task is assigned / decided. */
public final class ApprovalNotificationPublisher {
    private final NotificationService notifications;

    public ApprovalNotificationPublisher(NotificationService notifications) {
        this.notifications = notifications;
    }

    public Notification taskAssigned(String recipientSubject, String taskTitle, String actor) {
        return notifications.enqueue(
                BuiltinTemplates.APPROVAL_ASSIGNED,
                recipientSubject,
                NotificationChannel.IN_APP,
                Map.of("taskTitle", taskTitle, "actor", actor));
    }

    public Notification approved(String recipientSubject, String taskTitle, String actor) {
        return notifications.enqueue(
                BuiltinTemplates.APPROVAL_APPROVED,
                recipientSubject,
                NotificationChannel.IN_APP,
                Map.of("taskTitle", taskTitle, "actor", actor));
    }

    public Notification rejected(String recipientSubject, String taskTitle, String actor) {
        return notifications.enqueue(
                BuiltinTemplates.APPROVAL_REJECTED,
                recipientSubject,
                NotificationChannel.IN_APP,
                Map.of("taskTitle", taskTitle, "actor", actor));
    }
}
