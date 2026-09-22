package one.nexus.notification.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.notification.Notification;
import one.nexus.notification.NotificationChannel;
import one.nexus.notification.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> enqueue(@Valid @RequestBody EnqueueRequest request) {
        NotificationChannel channel = request.channel() == null
                ? NotificationChannel.IN_APP
                : NotificationChannel.valueOf(request.channel());
        Notification notification = notifications.enqueue(
                request.templateCode(), request.recipientSubject(), channel, request.variables());
        return toJson(notification);
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return notifications.listMine().stream().map(NotificationController::toJson).toList();
    }

    @PostMapping("/{id}/read")
    public Map<String, Object> read(@PathVariable UUID id) {
        return toJson(notifications.markRead(id));
    }

    private static Map<String, Object> toJson(Notification notification) {
        return Map.of(
                "notification_id", notification.id().toString(),
                "tenant_id", notification.tenantId().toString(),
                "recipient_subject", notification.recipientSubject(),
                "template_code", notification.templateCode(),
                "title", notification.title(),
                "body", notification.body(),
                "status", notification.status().name(),
                "outbox_message_id", notification.outboxMessageId() == null
                        ? ""
                        : notification.outboxMessageId().toString(),
                "created_at", notification.createdAt().toString());
    }

    public record EnqueueRequest(
            @NotBlank String templateCode,
            @NotBlank String recipientSubject,
            String channel,
            Map<String, String> variables) {}
}
