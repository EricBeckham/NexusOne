package one.nexus.p5.connect;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ConnectModels {
    private ConnectModels() {}
    public enum DeliveryStatus { PENDING, DELIVERED, RETRY, FAILED }
    public record ApiClient(UUID id, String name, String keyPrefix, byte[] secretDigest, List<String> scopes,
                            int rateLimitPerMinute, boolean active, Instant createdAt) {
        public ApiClient { secretDigest = secretDigest.clone(); scopes = List.copyOf(scopes); }
        @Override public byte[] secretDigest() { return secretDigest.clone(); }
    }
    public record IssuedClient(ApiClient client, String secret) {}
    public record InternalCall(UUID clientId, String scope, String idempotencyKey, Map<String,Object> response, boolean replayed) {}
    public record WebhookSubscription(UUID id, String name, String endpoint, List<String> eventTypes,
                                      boolean active, Instant createdAt) { public WebhookSubscription { eventTypes=List.copyOf(eventTypes); } }
    public record WebhookDelivery(UUID id, UUID subscriptionId, String eventId, String eventType,
                                  Map<String,Object> payload, DeliveryStatus status, int attempts,
                                  Instant nextAttemptAt, String lastError, Instant createdAt, Instant deliveredAt) {}
    public record ConnectorDefinition(String code, String name, String type, boolean active) {}
    public record ConnectorRun(UUID id, String connectorCode, String status, Map<String,Object> output,
                               String error, Instant startedAt, Instant finishedAt) {}
}
