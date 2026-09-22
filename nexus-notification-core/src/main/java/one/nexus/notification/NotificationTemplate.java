package one.nexus.notification;

import java.util.List;
import java.util.Objects;

public record NotificationTemplate(
        String code,
        NotificationChannel channel,
        String titleTemplate,
        String bodyTemplate,
        List<String> requiredVars) {
    public NotificationTemplate {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(titleTemplate, "titleTemplate");
        Objects.requireNonNull(bodyTemplate, "bodyTemplate");
        requiredVars = List.copyOf(requiredVars == null ? List.of() : requiredVars);
    }
}
