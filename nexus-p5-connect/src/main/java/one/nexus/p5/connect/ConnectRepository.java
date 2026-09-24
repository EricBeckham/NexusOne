package one.nexus.p5.connect;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p5.connect.ConnectModels.*;

public interface ConnectRepository {
    void saveClient(ApiClient client);
    Optional<ApiClient> client(UUID id);
    List<ApiClient> clients();
    Optional<ApiClient> clientForCurrentTenant(UUID id);
    boolean consumeRate(UUID clientId, Instant minute, int limit);
    Optional<Map<String,Object>> idempotentResponse(UUID clientId, String key);
    void saveIdempotentResponse(UUID clientId, String key, Map<String,Object> response, Instant createdAt);
    void saveCallLog(ApiCallLog log);
    List<ApiCallLog> callLogs();
    void saveSubscription(WebhookSubscription subscription);
    Optional<WebhookSubscription> subscription(UUID id);
    List<WebhookSubscription> subscriptions();
    Optional<WebhookSubscription> subscriptionForCurrentTenant(UUID id);
    List<WebhookSubscription> subscriptionsFor(String eventType, UUID tenantId);
    void saveDelivery(WebhookDelivery delivery);
    List<WebhookDelivery> dueDeliveries(Instant now, int limit);
    List<WebhookDelivery> deliveriesForSubscription(UUID subscriptionId);
    void saveConnectorRun(ConnectorRun run);
    List<ConnectorRun> connectorRuns();
}
