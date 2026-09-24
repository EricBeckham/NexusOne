package one.nexus.p5.connect;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ConnectModels {
    private ConnectModels() {}
    public enum DeliveryStatus { PENDING, DELIVERED, RETRY, FAILED }
    public record ApiClient(UUID id, UUID tenantId, String name, String keyPrefix, byte[] secretDigest, List<String> scopes,
                            int rateLimitPerMinute, boolean active, Instant createdAt) {
        public ApiClient { secretDigest = secretDigest.clone(); scopes = List.copyOf(scopes); }
        @Override public byte[] secretDigest() { return secretDigest.clone(); }
    }
    public record ApiClientView(UUID id, UUID tenantId, String name, String keyPrefix, List<String> scopes,
                                int rateLimitPerMinute, boolean active, Instant createdAt) {
        public ApiClientView { scopes = List.copyOf(scopes); }
        public static ApiClientView from(ApiClient client) {
            return new ApiClientView(client.id(), client.tenantId(), client.name(), client.keyPrefix(),
                    client.scopes(), client.rateLimitPerMinute(), client.active(), client.createdAt());
        }
    }
    public record IssuedClient(ApiClient client, String secret) {}
    public record InternalCall(UUID clientId, String scope, String idempotencyKey, Map<String,Object> response, boolean replayed) {}
    public record ApiCallLog(UUID id, UUID tenantId, UUID clientId, String scope, String apiPath,
                             String httpMethod, String status, String traceId, Instant createdAt) {}
    public record WebhookSubscription(UUID id, UUID tenantId, String name, String endpoint, List<String> eventTypes,
                                      boolean active, Instant createdAt) { public WebhookSubscription { eventTypes=List.copyOf(eventTypes); } }
    public record WebhookDelivery(UUID id, UUID tenantId, UUID subscriptionId, String eventId, String eventType,
                                  Map<String,Object> payload, DeliveryStatus status, int attempts,
                                  Instant nextAttemptAt, String lastError, Instant createdAt, Instant deliveredAt) {}
    public record WebhookDeliveryView(UUID id, UUID subscriptionId, String eventId, String eventType,
                                     DeliveryStatus status, int attempts, Instant nextAttemptAt,
                                     Instant createdAt, Instant deliveredAt) {
        public static WebhookDeliveryView from(WebhookDelivery delivery) {
            return new WebhookDeliveryView(delivery.id(), delivery.subscriptionId(), delivery.eventId(),
                    delivery.eventType(), delivery.status(), delivery.attempts(), delivery.nextAttemptAt(),
                    delivery.createdAt(), delivery.deliveredAt());
        }
    }
    public record ConnectorDefinition(String code, String name, String type, boolean active) {}
    public record ConnectorRun(UUID id, UUID tenantId, String connectorCode, String status, Map<String,Object> output,
                               String error, Instant startedAt, Instant finishedAt) {}
}
