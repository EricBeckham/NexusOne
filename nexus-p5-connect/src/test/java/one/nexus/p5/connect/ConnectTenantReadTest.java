package one.nexus.p5.connect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.RenderedSql;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConnectTenantReadTest {
    private static final UUID TENANT_ID = UUID.fromString("01900000-0000-7000-8000-000000000011");

    @AfterEach
    void clearContext() {
        AccessContextHolder.clear();
    }

    @Test
    void clientWebhookAndCallLogQueriesCompileWithTheIssuedTenantPredicate() {
        IsolationKernel kernel = ConnectIsolationConfiguration.createKernel(new CollectingAuditSink());
        var context = kernel.tenants().issue(
                new AuthenticatedMembership(
                        Actor.person("connect-reader"),
                        TENANT_ID.toString(),
                        TENANT_ID.toString(),
                        Set.of(TENANT_ID.toString())),
                "connect-read-trace");

        List<RenderedSql> queries = AccessContextHolder.call(context, () -> List.of(
                kernel.compiler().compile(QueryAst.select(
                        "integration.api_client",
                        List.of("id", "tenant_id", "name", "key_prefix", "secret_digest", "scopes",
                                "rate_limit_per_minute", "active", "created_at"),
                        Map.of()),
                        context),
                kernel.compiler().compile(QueryAst.select(
                        "integration.webhook_subscription",
                        List.of("id", "tenant_id", "name", "endpoint", "event_types", "active", "created_at"),
                        Map.of()),
                        context),
                kernel.compiler().compile(QueryAst.select(
                        "integration.api_call_log",
                        List.of("id", "tenant_id", "client_id", "scope", "api_path", "http_method",
                                "status", "trace_id", "created_at"),
                        Map.of()),
                        context)));

        assertTrue(queries.stream().allMatch(query -> query.sql().contains("\"tenant_id\" = ?")));
        assertTrue(queries.stream().allMatch(query -> query.parameters().contains(TENANT_ID)));
        assertEquals("TenantContext", queries.getFirst().plan().tenantSource());
    }
}
