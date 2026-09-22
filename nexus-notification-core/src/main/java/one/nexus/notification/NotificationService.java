package one.nexus.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.common.id.UuidV7;
import one.nexus.event.OutboxAppendCommand;
import one.nexus.event.OutboxPort;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.boundary.SideEffectGuard;
import one.nexus.isolation.context.AccessContextHolder;

/**
 * Sync API + transactional {@link OutboxPort#append}. Does not send email/SMS.
 * Async retry/dispatch is M0-09 {@code OutboxDispatcher}.
 */
public final class NotificationService {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_]*)\\}");

    private final TemplateCatalog templates;
    private final NotificationRepository repository;
    private final OutboxPort outbox;
    private final Clock clock;

    public NotificationService(
            TemplateCatalog templates,
            NotificationRepository repository,
            OutboxPort outbox,
            Clock clock) {
        this.templates = Objects.requireNonNull(templates, "templates");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Notification enqueue(
            String templateCode,
            String recipientSubject,
            NotificationChannel channel,
            Map<String, String> variables) {
        if (channel != null && channel != NotificationChannel.IN_APP) {
            throw new NexusException(
                    ErrorCode.NOTIFY_CHANNEL_UNSUPPORTED,
                    400,
                    "M0 in-app only; email/SMS business notify is out of scope");
        }
        AccessContext context = AccessContextHolder.current();
        if (!(context instanceof TenantContext tenant)) {
            throw new NexusException(ErrorCode.CTX_MISSING_TENANT, 403, "TenantContext is required");
        }
        UUID tenantId = UUID.fromString(tenant.tenantId());
        NotificationTemplate template = templates.find(templateCode)
                .orElseThrow(() -> new NexusException(ErrorCode.NOTIFY_TEMPLATE_MISSING, 404, "template not found"));
        if (template.channel() != NotificationChannel.IN_APP) {
            throw new NexusException(ErrorCode.NOTIFY_CHANNEL_UNSUPPORTED, 400, "template is not in-app");
        }
        Map<String, String> vars = variables == null ? Map.of() : Map.copyOf(variables);
        for (String required : template.requiredVars()) {
            if (!vars.containsKey(required) || vars.get(required) == null || vars.get(required).isBlank()) {
                throw new NexusException(ErrorCode.NOTIFY_INVALID, 400, "missing template variable " + required);
            }
        }
        String title = render(template.titleTemplate(), vars);
        String body = render(template.bodyTemplate(), vars);
        UUID id = UuidV7.generate();
        String payload = payloadJson(id, template.code(), recipientSubject, vars);
        Notification notification = new Notification(
                id,
                tenantId,
                recipientSubject,
                template.code(),
                title,
                body,
                payload,
                NotificationStatus.PENDING,
                null,
                Instant.now(clock),
                null);
        repository.insert(notification);
        UUID outboxId = outbox.append(new OutboxAppendCommand(
                OutboxAppendCommand.NOTIFY_INBOX,
                "notify.in_app.deliver",
                1,
                payload,
                "notification",
                id,
                context.traceId(),
                null));
        Notification after = repository.find(id).orElse(notification).withOutbox(outboxId);
        repository.update(after);
        return after;
    }

    public List<Notification> listMine() {
        AccessContext context = AccessContextHolder.current();
        UUID tenantId = UUID.fromString(context.boundTenantId().orElseThrow(() ->
                new NexusException(ErrorCode.CTX_MISSING_TENANT, 403, "TenantContext is required")));
        return repository.listByRecipient(tenantId, context.actor().actorId());
    }

    public Notification markRead(UUID id) {
        Notification notification = repository.find(id)
                .orElseThrow(() -> new NexusException(ErrorCode.NOTIFY_NOT_FOUND, 404, "notification not found"));
        SideEffectGuard.assertSameTenant(AccessContextHolder.current(), notification.tenantId().toString());
        if (!notification.recipientSubject().equals(AccessContextHolder.current().actor().actorId())) {
            throw new NexusException(ErrorCode.NOTIFY_NOT_FOUND, 404, "notification not found");
        }
        Notification read = notification.read(Instant.now(clock));
        repository.update(read);
        return read;
    }

    public void markDelivered(UUID id) {
        Notification notification = repository.find(id)
                .orElseThrow(() -> new NexusException(ErrorCode.NOTIFY_NOT_FOUND, 404, "notification not found"));
        repository.update(notification.delivered());
    }

    static String render(String template, Map<String, String> variables) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String value = variables.getOrDefault(matcher.group(1), matcher.group(0));
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String payloadJson(UUID id, String template, String recipient, Map<String, String> vars) {
        StringBuilder json = new StringBuilder();
        json.append("{\"notification_id\":\"").append(id).append("\",");
        json.append("\"template_code\":\"").append(escape(template)).append("\",");
        json.append("\"recipient_subject\":\"").append(escape(recipient)).append("\",");
        json.append("\"variables\":{");
        boolean first = true;
        for (Map.Entry<String, String> entry : new LinkedHashMap<>(vars).entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(escape(entry.getKey())).append("\":\"")
                    .append(escape(entry.getValue())).append('"');
        }
        json.append("}}");
        return json.toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
