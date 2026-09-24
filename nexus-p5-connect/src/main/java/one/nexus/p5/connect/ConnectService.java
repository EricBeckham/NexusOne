package one.nexus.p5.connect;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import one.nexus.audit.AuditPort;
import one.nexus.common.audit.AuditCategory;
import one.nexus.common.audit.AuditEvent;
import one.nexus.crypto.CryptoPort;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p5.connect.ConnectModels.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class ConnectService {
    public static final String SAMPLE_CONNECTOR = "nexus-self-health";
    private static final String CALL_PATH = "/internal/call";

    private final ConnectRepository repository;
    private final CryptoPort crypto;
    private final AuditPort audit;
    private final WebhookTransport transport;
    private final ObjectMapper json;
    private final ConnectIsolation isolation;
    private final Clock clock;

    @Autowired
    public ConnectService(
            ConnectRepository repository,
            CryptoPort crypto,
            AuditPort audit,
            WebhookTransport transport,
            ObjectMapper json,
            ConnectIsolation connectIsolation) {
        this(repository, crypto, audit, transport, json, connectIsolation, Clock.systemUTC());
    }

    ConnectService(
            ConnectRepository repository,
            CryptoPort crypto,
            AuditPort audit,
            WebhookTransport transport,
            ObjectMapper json,
            ConnectIsolation connectIsolation,
            Clock clock) {
        this.repository = repository;
        this.crypto = crypto;
        this.audit = audit;
        this.transport = transport;
        this.json = json;
        this.isolation = connectIsolation;
        this.clock = clock;
    }

    @Transactional
    public IssuedClient issueClient(String name, List<String> scopes, int rateLimit) {
        if (name == null || name.isBlank() || scopes == null || scopes.isEmpty() || rateLimit < 1) {
            throw new IllegalArgumentException("name, scopes and positive rate limit required");
        }
        UUID tenantId = requireTenantId();
        UUID id = UUID.randomUUID();
        String secret = "nx_" + UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        ApiClient client = new ApiClient(
                id,
                tenantId,
                name,
                secret.substring(0, 10),
                crypto.digest(secret.getBytes(StandardCharsets.UTF_8)),
                scopes,
                rateLimit,
                true,
                clock.instant());
        repository.saveClient(client);
        append("p5.client.issued", id, "api_client", id.toString(), "issued");
        return new IssuedClient(client, secret);
    }

    @Transactional
    public InternalCall invoke(
            UUID clientId,
            String secret,
            String requiredScope,
            String idempotencyKey,
            Map<String, Object> request) {
        ApiClient client = repository.client(clientId).orElse(null);
        if (client == null) {
            reject(clientId, "client not found");
            throw new SecurityException("client not found");
        }
        TenantContext context = apiClientContext(client);
        return AccessContextHolder.call(
                context,
                () -> invokeInTenant(client, secret, requiredScope, idempotencyKey, request));
    }

    private InternalCall invokeInTenant(
            ApiClient client,
            String secret,
            String requiredScope,
            String idempotencyKey,
            Map<String, Object> request) {
        String traceId = UUID.randomUUID().toString();
        if (!client.active() || !constantTime(client.secretDigest(), crypto.digest(text(secret)))) {
            recordCall(client, requiredScope, "DENIED", traceId);
            reject(client.id(), "invalid client credential", traceId);
            throw new SecurityException("invalid client credential");
        }
        if (requiredScope == null || !client.scopes().contains(requiredScope)) {
            recordCall(client, requiredScope, "DENIED", traceId);
            reject(client.id(), "scope denied", traceId);
            throw new SecurityException("scope denied");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            recordCall(client, requiredScope, "BAD_REQUEST", traceId);
            throw new IllegalArgumentException("Idempotency-Key required");
        }
        var replay = repository.idempotentResponse(client.id(), idempotencyKey);
        if (replay.isPresent()) {
            recordCall(client, requiredScope, "REPLAYED", traceId);
            return new InternalCall(client.id(), requiredScope, idempotencyKey, replay.get(), true);
        }
        Instant minute = clock.instant().truncatedTo(ChronoUnit.MINUTES);
        if (!repository.consumeRate(client.id(), minute, client.rateLimitPerMinute())) {
            recordCall(client, requiredScope, "RATE_LIMITED", traceId);
            reject(client.id(), "rate limit exceeded", traceId);
            throw new RateLimitException("rate limit exceeded");
        }
        Map<String, Object> response = Map.of(
                "accepted", true,
                "clientId", client.id().toString(),
                "scope", requiredScope,
                "request", request == null ? Map.of() : request);
        repository.saveIdempotentResponse(client.id(), idempotencyKey, response, clock.instant());
        recordCall(client, requiredScope, "SUCCEEDED", traceId);
        append("p5.api.called", client.id(), "api_client", client.id().toString(), requiredScope, traceId);
        return new InternalCall(client.id(), requiredScope, idempotencyKey, response, false);
    }

    @Transactional
    public WebhookSubscription subscribe(String name, String endpoint, List<String> events) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("webhook name required");
        }
        URI target;
        try {
            target = URI.create(endpoint);
        } catch (Exception ex) {
            throw new IllegalArgumentException("valid webhook endpoint required");
        }
        if (!"https".equalsIgnoreCase(target.getScheme()) || target.getHost() == null || target.getUserInfo() != null) {
            throw new IllegalArgumentException("webhook endpoint must be an https URL without user info");
        }
        if (events == null || events.isEmpty()) {
            throw new IllegalArgumentException("event types required");
        }
        WebhookSubscription subscription = new WebhookSubscription(
                UUID.randomUUID(), requireTenantId(), name, endpoint, events, true, clock.instant());
        repository.saveSubscription(subscription);
        append(
                "p5.webhook.subscribed",
                subscription.id(),
                "webhook_subscription",
                subscription.id().toString(),
                endpoint);
        return subscription;
    }

    @Transactional
    public int publish(String eventId, String eventType, Map<String, Object> payload) {
        if (eventId == null || eventId.isBlank() || eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("event id and type required");
        }
        UUID tenantId = requireTenantId();
        Map<String, Object> safePayload = payload == null ? Map.of() : Map.copyOf(payload);
        int count = 0;
        for (WebhookSubscription subscription : repository.subscriptionsFor(eventType, tenantId)) {
            WebhookDelivery delivery = new WebhookDelivery(
                    UUID.randomUUID(),
                    tenantId,
                    subscription.id(),
                    eventId,
                    eventType,
                    safePayload,
                    DeliveryStatus.PENDING,
                    0,
                    clock.instant(),
                    null,
                    clock.instant(),
                    null);
            repository.saveDelivery(delivery);
            count++;
        }
        return count;
    }

    @Transactional
    public List<WebhookDelivery> dispatchDue(int limit) {
        UUID tenantId = requireTenantId();
        List<WebhookDelivery> due = repository.dueDeliveries(clock.instant(), Math.max(1, Math.min(limit, 100)));
        for (WebhookDelivery delivery : due) {
            assertTenant(tenantId, delivery.tenantId());
            dispatch(delivery);
        }
        return due;
    }

    private void dispatch(WebhookDelivery delivery) {
        Instant now = clock.instant();
        int attempts = delivery.attempts() + 1;
        try {
            WebhookSubscription subscription = repository.subscription(delivery.subscriptionId())
                    .orElseThrow(() -> new IllegalStateException("webhook not found in current tenant"));
            String body = json.writeValueAsString(Map.of(
                    "id", delivery.eventId(),
                    "type", delivery.eventType(),
                    "payload", delivery.payload()));
            String timestamp = Long.toString(now.getEpochSecond());
            String signature = Base64.getEncoder().encodeToString(
                    crypto.mac((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
            transport.post(
                    subscription.endpoint(),
                    body,
                    Map.of(
                            "X-Nexus-Event-Id", delivery.eventId(),
                            "X-Nexus-Delivery-Attempt", Integer.toString(attempts),
                            "X-Nexus-Signature-Timestamp", timestamp,
                            "X-Nexus-Signature", signature));
            repository.saveDelivery(new WebhookDelivery(
                    delivery.id(),
                    delivery.tenantId(),
                    delivery.subscriptionId(),
                    delivery.eventId(),
                    delivery.eventType(),
                    delivery.payload(),
                    DeliveryStatus.DELIVERED,
                    attempts,
                    now,
                    null,
                    delivery.createdAt(),
                    now));
        } catch (Exception ex) {
            DeliveryStatus status = attempts >= 5 ? DeliveryStatus.FAILED : DeliveryStatus.RETRY;
            Instant next = now.plusSeconds(Math.min(300, 1L << Math.min(attempts, 8)));
            repository.saveDelivery(new WebhookDelivery(
                    delivery.id(),
                    delivery.tenantId(),
                    delivery.subscriptionId(),
                    delivery.eventId(),
                    delivery.eventType(),
                    delivery.payload(),
                    status,
                    attempts,
                    next,
                    safe(ex.getMessage()),
                    delivery.createdAt(),
                    null));
        }
    }

    public List<ConnectorDefinition> connectors() {
        return List.of(new ConnectorDefinition(SAMPLE_CONNECTOR, "NexusOne 自检", "INTERNAL", true));
    }

    @Transactional
    public ConnectorRun runConnector(String code) {
        if (!SAMPLE_CONNECTOR.equals(code)) {
            throw new IllegalArgumentException("connector not found");
        }
        UUID tenantId = requireTenantId();
        Instant started = clock.instant();
        Map<String, Object> output = Map.of("status", "UP", "service", "nexusone", "checkedAt", started.toString());
        ConnectorRun run = new ConnectorRun(
                UUID.randomUUID(), tenantId, code, "SUCCEEDED", output, null, started, clock.instant());
        repository.saveConnectorRun(run);
        append("p5.connector.ran", run.id(), "connector_run", run.id().toString(), code);
        return run;
    }

    @Transactional(readOnly = true)
    public List<ConnectorRun> runs() {
        UUID tenantId = requireTenantId();
        List<ConnectorRun> rows = repository.connectorRuns();
        rows.forEach(row -> assertTenant(tenantId, row.tenantId()));
        return rows;
    }

    @Transactional(readOnly = true)
    public List<ApiClientView> clients() {
        UUID tenantId = requireTenantId();
        List<ApiClient> rows = repository.clients();
        rows.forEach(row -> assertTenant(tenantId, row.tenantId()));
        return rows.stream().map(ApiClientView::from).toList();
    }

    @Transactional(readOnly = true)
    public ApiClientView client(UUID id) {
        UUID tenantId = requireTenantId();
        ApiClient row = repository.clientForCurrentTenant(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API client not found"));
        assertTenant(tenantId, row.tenantId());
        return ApiClientView.from(row);
    }

    @Transactional(readOnly = true)
    public List<WebhookSubscription> subscriptions() {
        UUID tenantId = requireTenantId();
        List<WebhookSubscription> rows = repository.subscriptions();
        rows.forEach(row -> assertTenant(tenantId, row.tenantId()));
        return rows;
    }

    @Transactional(readOnly = true)
    public WebhookSubscription subscription(UUID id) {
        UUID tenantId = requireTenantId();
        WebhookSubscription row = repository.subscriptionForCurrentTenant(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "webhook not found"));
        assertTenant(tenantId, row.tenantId());
        return row;
    }

    @Transactional(readOnly = true)
    public List<WebhookDeliveryView> deliveries(UUID subscriptionId) {
        subscription(subscriptionId);
        UUID tenantId = requireTenantId();
        List<WebhookDelivery> rows = repository.deliveriesForSubscription(subscriptionId);
        rows.forEach(row -> assertTenant(tenantId, row.tenantId()));
        return rows.stream().map(WebhookDeliveryView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ApiCallLog> callLogs() {
        UUID tenantId = requireTenantId();
        List<ApiCallLog> rows = repository.callLogs();
        rows.forEach(row -> assertTenant(tenantId, row.tenantId()));
        return rows;
    }

    private TenantContext apiClientContext(ApiClient client) {
        String tenantId = client.tenantId().toString();
        String traceId = UUID.randomUUID().toString();
        return isolation.kernel().tenants().issue(
                new AuthenticatedMembership(
                        Actor.system(client.id().toString()),
                        tenantId,
                        client.id().toString(),
                        Set.of(tenantId)),
                traceId);
    }

    private void recordCall(ApiClient client, String scope, String status, String traceId) {
        repository.saveCallLog(new ApiCallLog(
                UUID.randomUUID(),
                client.tenantId(),
                client.id(),
                scope == null ? "" : scope,
                CALL_PATH,
                "POST",
                status,
                traceId,
                clock.instant()));
    }

    private void append(String type, UUID actor, String resource, String id, String action) {
        append(type, actor, resource, id, action, UUID.randomUUID().toString());
    }

    private void append(String type, UUID actor, String resource, String id, String action, String trace) {
        audit.append(new AuditEvent(
                UUID.randomUUID(),
                1,
                clock.instant(),
                AuditCategory.DATA,
                type,
                null,
                actor,
                null,
                true,
                "p5-internal",
                trace,
                trace,
                null,
                AuditEvent.OUTCOME_ACCEPTED,
                resource,
                id,
                Map.of("action", action)));
    }

    private void reject(UUID actor, String reason) {
        reject(actor, reason, UUID.randomUUID().toString());
    }

    private void reject(UUID actor, String reason, String trace) {
        audit.appendNewTransaction(new AuditEvent(
                UUID.randomUUID(),
                1,
                clock.instant(),
                AuditCategory.GUARD_REJECT,
                "p5.api.rejected",
                null,
                actor,
                null,
                true,
                "p5-internal",
                trace,
                trace,
                null,
                AuditEvent.OUTCOME_REJECTED,
                "api_client",
                actor.toString(),
                Map.of("reason", reason)));
    }

    private static UUID requireTenantId() {
        AccessContext context = AccessContextHolder.currentOrNull();
        if (!(context instanceof TenantContext tenant)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TenantContext is required");
        }
        try {
            return UUID.fromString(tenant.tenantId());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TenantContext has an invalid tenant id");
        }
    }

    private static void assertTenant(UUID expected, UUID actual) {
        if (!expected.equals(actual)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "tenant scope mismatch");
        }
    }

    private static byte[] text(String value) {
        return (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
    }

    private static boolean constantTime(byte[] left, byte[] right) {
        if (left.length != right.length) {
            return false;
        }
        int difference = 0;
        for (int i = 0; i < left.length; i++) {
            difference |= left[i] ^ right[i];
        }
        return difference == 0;
    }

    private static String safe(String value) {
        if (value == null) {
            return "delivery failed";
        }
        return value.substring(0, Math.min(value.length(), 500));
    }
}
