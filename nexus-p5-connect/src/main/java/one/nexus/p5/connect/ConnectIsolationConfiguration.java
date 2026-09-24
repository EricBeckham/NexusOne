package one.nexus.p5.connect;

import java.util.Set;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.IsolationAuditSink;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.query.TableRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConnectIsolationConfiguration {
    @Bean
    ConnectIsolation connectIsolation(ObjectProvider<IsolationAuditSink> sinks) {
        return createIsolation(sinks.getIfAvailable(CollectingAuditSink::new));
    }

    static ConnectIsolation createIsolation(IsolationAuditSink sink) {
        return new ConnectIsolation(createKernel(sink));
    }

    static IsolationKernel createKernel(IsolationAuditSink sink) {
        TableRegistry tables = new TableRegistry()
                .registerTenant(
                        "integration.api_client",
                        "nexus-p5-connect",
                        1,
                        "id",
                        "tenant_id",
                        "name",
                        "key_prefix",
                        "secret_digest",
                        "scopes",
                        "rate_limit_per_minute",
                        "active",
                        "created_at")
                .registerTenant(
                        "integration.api_idempotency",
                        "nexus-p5-connect",
                        1,
                        "client_id",
                        "idempotency_key",
                        "tenant_id",
                        "response",
                        "created_at")
                .registerTenant(
                        "integration.api_call_log",
                        "nexus-p5-connect",
                        1,
                        "id",
                        "tenant_id",
                        "client_id",
                        "scope",
                        "api_path",
                        "http_method",
                        "status",
                        "trace_id",
                        "created_at")
                .registerTenant(
                        "integration.webhook_subscription",
                        "nexus-p5-connect",
                        1,
                        "id",
                        "tenant_id",
                        "name",
                        "endpoint",
                        "event_types",
                        "active",
                        "created_at")
                .registerTenant(
                        "integration.webhook_delivery",
                        "nexus-p5-connect",
                        1,
                        "id",
                        "tenant_id",
                        "subscription_id",
                        "event_id",
                        "event_type",
                        "payload",
                        "status",
                        "attempts",
                        "next_attempt_at",
                        "last_error",
                        "created_at",
                        "delivered_at")
                .registerTenant(
                        "integration.connector_run",
                        "nexus-p5-connect",
                        1,
                        "id",
                        "tenant_id",
                        "connector_code",
                        "status",
                        "output",
                        "error",
                        "started_at",
                        "finished_at");
        return IsolationKernel.create(tables.freeze(), Set.of(), sink);
    }
}
