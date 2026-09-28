package one.nexus.p3.application;

import java.util.UUID;
import one.nexus.notification.ApprovalNotificationPublisher;
import one.nexus.notification.BuiltinTemplates;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * PLC1: in-app notices for the product-application path only.
 * Ledger approvals already notify from {@code BusinessPathService}, so this ignores other business keys.
 * Titles are task names, never submitted field values.
 */
@Component
public class ProductApplicationNotifier {
    static final String KEY_PREFIX = "product-application:";

    private final ObjectProvider<ApprovalNotificationPublisher> notifications;

    public ProductApplicationNotifier(ObjectProvider<ApprovalNotificationPublisher> notifications) {
        this.notifications = notifications;
    }

    public void assigned(UUID recipientPersonId, String title, UUID actorPersonId) {
        send(BuiltinTemplates.APPROVAL_ASSIGNED, recipientPersonId, title, actorPersonId);
    }

    public void decided(String businessKey, String outcome, UUID initiatorPersonId, UUID actorPersonId, String title) {
        if (businessKey == null || !businessKey.startsWith(KEY_PREFIX)) {
            return;
        }
        String template = switch (outcome) {
            case "APPROVE" -> BuiltinTemplates.APPROVAL_APPROVED;
            case "REJECT" -> BuiltinTemplates.APPROVAL_REJECTED;
            default -> null;
        };
        if (template == null) {
            return;
        }
        send(template, initiatorPersonId, title, actorPersonId);
    }

    private void send(String template, UUID recipientPersonId, String title, UUID actorPersonId) {
        if (recipientPersonId == null || actorPersonId == null) {
            return;
        }
        ApprovalNotificationPublisher publisher = notifications == null ? null : notifications.getIfAvailable();
        if (publisher == null) {
            return;
        }
        String taskTitle = title == null || title.isBlank() ? "审批" : title;
        String actor = actorPersonId.toString();
        String recipient = recipientPersonId.toString();
        if (BuiltinTemplates.APPROVAL_ASSIGNED.equals(template)) {
            publisher.taskAssigned(recipient, taskTitle, actor);
        } else if (BuiltinTemplates.APPROVAL_APPROVED.equals(template)) {
            publisher.approved(recipient, taskTitle, actor);
        } else {
            publisher.rejected(recipient, taskTitle, actor);
        }
    }
}
