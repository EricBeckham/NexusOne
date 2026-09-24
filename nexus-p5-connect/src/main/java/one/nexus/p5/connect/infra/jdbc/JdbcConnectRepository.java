package one.nexus.p5.connect.infra.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.QueryPage;
import one.nexus.isolation.api.QueryRange;
import one.nexus.isolation.api.QuerySort;
import one.nexus.isolation.api.RenderedSql;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p5.connect.ConnectIsolation;
import one.nexus.p5.connect.ConnectModels.*;
import one.nexus.p5.connect.ConnectRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
@DependsOn("flyway")
public class JdbcConnectRepository implements ConnectRepository {
    private static final int READ_LIMIT = 1000;

    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final ConnectIsolation isolation;

    public JdbcConnectRepository(
            JdbcClient jdbc,
            ObjectMapper json,
            @Qualifier("connectIsolation") ConnectIsolation connectIsolation) {
        this.jdbc = jdbc;
        this.json = json;
        this.isolation = connectIsolation;
    }

    @Override
    public void saveClient(ApiClient client) {
        assertCurrentTenant(client.tenantId());
        jdbc.sql("""
                INSERT INTO integration.api_client(id,tenant_id,name,key_prefix,secret_digest,scopes,rate_limit_per_minute,active,created_at)
                VALUES(:id,:tenant,:name,:prefix,:digest,CAST(:scopes AS jsonb),:rate,:active,:created)
                ON CONFLICT(id) DO UPDATE SET name=:name,scopes=CAST(:scopes AS jsonb),
                    rate_limit_per_minute=:rate,active=:active
                WHERE integration.api_client.tenant_id=:tenant
                """)
                .param("id", client.id())
                .param("tenant", client.tenantId())
                .param("name", client.name())
                .param("prefix", client.keyPrefix())
                .param("digest", client.secretDigest())
                .param("scopes", write(client.scopes()))
                .param("rate", client.rateLimitPerMinute())
                .param("active", client.active())
                .param("created", timestamp(client.createdAt()))
                .update();
    }

    @Override
    public Optional<ApiClient> client(UUID id) {
        return jdbc.sql("SELECT * FROM integration.api_client WHERE id=:id")
                .param("id", id)
                .query(this::mapClient)
                .optional();
    }

    @Override
    public List<ApiClient> clients() {
        return tenantRows(
                "integration.api_client",
                List.of("id", "tenant_id", "name", "key_prefix", "secret_digest", "scopes",
                        "rate_limit_per_minute", "active", "created_at"),
                Map.of(),
                this::mapClient);
    }

    @Override
    public Optional<ApiClient> clientForCurrentTenant(UUID id) {
        return tenantRow(
                "integration.api_client",
                List.of("id", "tenant_id", "name", "key_prefix", "secret_digest", "scopes",
                        "rate_limit_per_minute", "active", "created_at"),
                Map.of("id", id),
                this::mapClient);
    }

    @Override
    public boolean consumeRate(UUID id, Instant minute, int limit) {
        return jdbc.sql("""
                INSERT INTO integration.api_rate_window(client_id,window_start,request_count) VALUES(:id,:minute,1)
                ON CONFLICT(client_id,window_start) DO UPDATE
                SET request_count=integration.api_rate_window.request_count+1
                WHERE integration.api_rate_window.request_count < :limit
                RETURNING request_count
                """)
                .param("id", id)
                .param("minute", timestamp(minute))
                .param("limit", limit)
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    @Override
    public Optional<Map<String, Object>> idempotentResponse(UUID clientId, String key) {
        return tenantRow(
                "integration.api_idempotency",
                List.of("response"),
                Map.of("client_id", clientId, "idempotency_key", key),
                (row, index) -> read(row.getString("response"), new TypeReference<Map<String, Object>>() {}));
    }

    @Override
    public void saveIdempotentResponse(UUID clientId, String key, Map<String, Object> response, Instant createdAt) {
        jdbc.sql("""
                INSERT INTO integration.api_idempotency(client_id,idempotency_key,tenant_id,response,created_at)
                VALUES(:id,:key,:tenant,CAST(:response AS jsonb),:created)
                ON CONFLICT(client_id,idempotency_key) DO NOTHING
                """)
                .param("id", clientId)
                .param("key", key)
                .param("tenant", requireTenantId())
                .param("response", write(response))
                .param("created", timestamp(createdAt))
                .update();
    }

    @Override
    public void saveCallLog(ApiCallLog log) {
        QueryAst insert = QueryAst.insert("integration.api_call_log", Map.of(
                "id", log.id(),
                "tenant_id", log.tenantId().toString(),
                "client_id", log.clientId(),
                "scope", log.scope(),
                "api_path", log.apiPath(),
                "http_method", log.httpMethod(),
                "status", log.status(),
                "trace_id", log.traceId(),
                "created_at", timestamp(log.createdAt())));
        executeUpdate(insert);
    }

    @Override
    public List<ApiCallLog> callLogs() {
        return tenantRows(
                "integration.api_call_log",
                List.of("id", "tenant_id", "client_id", "scope", "api_path", "http_method", "status",
                        "trace_id", "created_at"),
                Map.of(),
                this::mapCallLog);
    }

    @Override
    public void saveSubscription(WebhookSubscription subscription) {
        assertCurrentTenant(subscription.tenantId());
        jdbc.sql("""
                INSERT INTO integration.webhook_subscription(id,tenant_id,name,endpoint,event_types,active,created_at)
                VALUES(:id,:tenant,:name,:endpoint,CAST(:events AS jsonb),:active,:created)
                ON CONFLICT(id) DO UPDATE SET name=:name,endpoint=:endpoint,event_types=CAST(:events AS jsonb),
                    active=:active
                WHERE integration.webhook_subscription.tenant_id=:tenant
                """)
                .param("id", subscription.id())
                .param("tenant", subscription.tenantId())
                .param("name", subscription.name())
                .param("endpoint", subscription.endpoint())
                .param("events", write(subscription.eventTypes()))
                .param("active", subscription.active())
                .param("created", timestamp(subscription.createdAt()))
                .update();
    }

    @Override
    public Optional<WebhookSubscription> subscription(UUID id) {
        return subscriptionForCurrentTenant(id);
    }

    @Override
    public List<WebhookSubscription> subscriptions() {
        return tenantRows(
                "integration.webhook_subscription",
                List.of("id", "tenant_id", "name", "endpoint", "event_types", "active", "created_at"),
                Map.of(),
                this::mapSubscription);
    }

    @Override
    public Optional<WebhookSubscription> subscriptionForCurrentTenant(UUID id) {
        return tenantRow(
                "integration.webhook_subscription",
                List.of("id", "tenant_id", "name", "endpoint", "event_types", "active", "created_at"),
                Map.of("id", id),
                this::mapSubscription);
    }

    @Override
    public List<WebhookSubscription> subscriptionsFor(String eventType, UUID tenantId) {
        assertCurrentTenant(tenantId);
        return subscriptions().stream()
                .filter(WebhookSubscription::active)
                .filter(subscription -> subscription.eventTypes().contains(eventType))
                .toList();
    }

    @Override
    public void saveDelivery(WebhookDelivery delivery) {
        assertCurrentTenant(delivery.tenantId());
        jdbc.sql("""
                INSERT INTO integration.webhook_delivery(
                    id,tenant_id,subscription_id,event_id,event_type,payload,status,attempts,
                    next_attempt_at,last_error,created_at,delivered_at)
                VALUES(:id,:tenant,:subscription,:event,:type,CAST(:payload AS jsonb),:status,:attempts,
                    :next,:error,:created,:delivered)
                ON CONFLICT(id) DO UPDATE SET status=:status,attempts=:attempts,next_attempt_at=:next,
                    last_error=:error,delivered_at=:delivered
                WHERE integration.webhook_delivery.tenant_id=:tenant
                """)
                .param("id", delivery.id())
                .param("tenant", delivery.tenantId())
                .param("subscription", delivery.subscriptionId())
                .param("event", delivery.eventId())
                .param("type", delivery.eventType())
                .param("payload", write(delivery.payload()))
                .param("status", delivery.status().name())
                .param("attempts", delivery.attempts())
                .param("next", timestamp(delivery.nextAttemptAt()))
                .param("error", delivery.lastError())
                .param("created", timestamp(delivery.createdAt()))
                .param("delivered", timestamp(delivery.deliveredAt()))
                .update();
    }

    @Override
    public List<WebhookDelivery> dueDeliveries(Instant now, int limit) {
        QueryAst query = QueryAst.select(
                        "integration.webhook_delivery",
                        List.of("id", "tenant_id", "subscription_id", "event_id", "event_type", "payload",
                                "status", "attempts", "next_attempt_at", "last_error", "created_at", "delivered_at"),
                        Map.of(
                                "status", List.of(DeliveryStatus.PENDING.name(), DeliveryStatus.RETRY.name()),
                                "next_attempt_at", QueryRange.lte(now)))
                .withSorts(List.of(QuerySort.asc("next_attempt_at")))
                .withPage(QueryPage.of(Math.max(1, Math.min(limit, READ_LIMIT)), 0));
        RenderedSql rendered = isolation.kernel().compiler().compile(query, AccessContextHolder.current());
        return jdbc.sql(rendered.sql()).params(rendered.parameters().toArray()).query(this::mapDelivery).list();
    }

    @Override
    public List<WebhookDelivery> deliveriesForSubscription(UUID subscriptionId) {
        return tenantRows(
                "integration.webhook_delivery",
                List.of("id", "tenant_id", "subscription_id", "event_id", "event_type", "payload",
                        "status", "attempts", "next_attempt_at", "last_error", "created_at", "delivered_at"),
                Map.of("subscription_id", subscriptionId),
                this::mapDelivery);
    }

    @Override
    public void saveConnectorRun(ConnectorRun run) {
        assertCurrentTenant(run.tenantId());
        jdbc.sql("""
                INSERT INTO integration.connector_run(id,tenant_id,connector_code,status,output,error,started_at,finished_at)
                VALUES(:id,:tenant,:code,:status,CAST(:output AS jsonb),:error,:started,:finished)
                """)
                .param("id", run.id())
                .param("tenant", run.tenantId())
                .param("code", run.connectorCode())
                .param("status", run.status())
                .param("output", write(run.output()))
                .param("error", run.error())
                .param("started", timestamp(run.startedAt()))
                .param("finished", timestamp(run.finishedAt()))
                .update();
    }

    @Override
    public List<ConnectorRun> connectorRuns() {
        return tenantRows(
                "integration.connector_run",
                List.of("id", "tenant_id", "connector_code", "status", "output", "error", "started_at", "finished_at"),
                Map.of(),
                "started_at",
                this::mapRun);
    }

    private <T> List<T> tenantRows(String table, List<String> columns, Map<String, Object> filters, RowMapper<T> mapper) {
        return tenantRows(table, columns, filters, "created_at", mapper);
    }

    private <T> List<T> tenantRows(
            String table,
            List<String> columns,
            Map<String, Object> filters,
            String orderColumn,
            RowMapper<T> mapper) {
        QueryAst query = QueryAst.select(table, columns, filters)
                .withSorts(List.of(QuerySort.desc(orderColumn)))
                .withPage(QueryPage.of(READ_LIMIT, 0));
        RenderedSql rendered = isolation.kernel().compiler().compile(query, AccessContextHolder.current());
        return jdbc.sql(rendered.sql()).params(rendered.parameters().toArray()).query(mapper).list();
    }

    private <T> Optional<T> tenantRow(String table, List<String> columns, Map<String, Object> filters, RowMapper<T> mapper) {
        QueryAst query = QueryAst.select(table, columns, filters).withPage(QueryPage.of(1, 0));
        RenderedSql rendered = isolation.kernel().compiler().compile(query, AccessContextHolder.current());
        return jdbc.sql(rendered.sql()).params(rendered.parameters().toArray()).query(mapper).optional();
    }

    private void executeUpdate(QueryAst query) {
        RenderedSql rendered = isolation.kernel().compiler().compile(query, AccessContextHolder.current());
        jdbc.sql(rendered.sql()).params(rendered.parameters().toArray()).update();
    }

    private ApiClient mapClient(ResultSet row, int index) throws SQLException {
        return new ApiClient(
                uuid(row, "id"),
                uuid(row, "tenant_id"),
                row.getString("name"),
                row.getString("key_prefix"),
                row.getBytes("secret_digest"),
                read(row.getString("scopes"), new TypeReference<List<String>>() {}),
                row.getInt("rate_limit_per_minute"),
                row.getBoolean("active"),
                instant(row, "created_at"));
    }

    private ApiCallLog mapCallLog(ResultSet row, int index) throws SQLException {
        return new ApiCallLog(
                uuid(row, "id"),
                uuid(row, "tenant_id"),
                uuid(row, "client_id"),
                row.getString("scope"),
                row.getString("api_path"),
                row.getString("http_method"),
                row.getString("status"),
                row.getString("trace_id"),
                instant(row, "created_at"));
    }

    private WebhookSubscription mapSubscription(ResultSet row, int index) throws SQLException {
        return new WebhookSubscription(
                uuid(row, "id"),
                uuid(row, "tenant_id"),
                row.getString("name"),
                row.getString("endpoint"),
                read(row.getString("event_types"), new TypeReference<List<String>>() {}),
                row.getBoolean("active"),
                instant(row, "created_at"));
    }

    private WebhookDelivery mapDelivery(ResultSet row, int index) throws SQLException {
        return new WebhookDelivery(
                uuid(row, "id"),
                uuid(row, "tenant_id"),
                uuid(row, "subscription_id"),
                row.getString("event_id"),
                row.getString("event_type"),
                read(row.getString("payload"), new TypeReference<Map<String, Object>>() {}),
                DeliveryStatus.valueOf(row.getString("status")),
                row.getInt("attempts"),
                instant(row, "next_attempt_at"),
                row.getString("last_error"),
                instant(row, "created_at"),
                instant(row, "delivered_at"));
    }

    private ConnectorRun mapRun(ResultSet row, int index) throws SQLException {
        return new ConnectorRun(
                uuid(row, "id"),
                uuid(row, "tenant_id"),
                row.getString("connector_code"),
                row.getString("status"),
                read(row.getString("output"), new TypeReference<Map<String, Object>>() {}),
                row.getString("error"),
                instant(row, "started_at"),
                instant(row, "finished_at"));
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private <T> T read(String value, TypeReference<T> type) {
        try {
            return json.readValue(value, type);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static UUID requireTenantId() {
        AccessContext context = AccessContextHolder.currentOrNull();
        if (!(context instanceof TenantContext tenant)) {
            throw new IllegalStateException("TenantContext is required");
        }
        return UUID.fromString(tenant.tenantId());
    }

    private static void assertCurrentTenant(UUID tenantId) {
        if (!requireTenantId().equals(tenantId)) {
            throw new IllegalStateException("tenant scope mismatch");
        }
    }

    private static UUID uuid(ResultSet row, String column) throws SQLException {
        Object value = row.getObject(column);
        return value instanceof UUID id ? id : UUID.fromString(value.toString());
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        var value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
