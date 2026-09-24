package one.nexus.bootstrap.demo;

import java.util.UUID;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** Inert P5 connector/call summaries for local screenshots; the client is disabled and has no usable secret. */
@Configuration
public class P5DemoSeedConfiguration {
    static final UUID CLIENT_ID = UUID.fromString("01900000-0000-7000-8000-000000000501");
    static final UUID CALL_LOG_ID = UUID.fromString("01900000-0000-7000-8000-000000000502");
    static final UUID RUN_ID = UUID.fromString("01900000-0000-7000-8000-000000000503");
    static final UUID TENANT_ID = P1DemoSeedConfiguration.TENANT_ID;

    @Bean
    ApplicationRunner p5DemoSeed(JdbcClient jdbc, Environment env, TransactionTemplate transaction) {
        return args -> {
            if (!DemoProfileGate.enabled(env.getProperty(DemoProfileGate.ENABLED))) {
                return;
            }
            String datasourceUrl = env.getProperty("spring.datasource.url", "");
            DemoProfileGate.assertAllowed(true, env.getProperty(DemoProfileGate.ENVIRONMENT, "local"), datasourceUrl);

            // Make the fixed tenant available even if runner ordering differs.
            new P1DemoSeedConfiguration().p1DemoSeed(jdbc, env, transaction).run(args);

            transaction.executeWithoutResult(status -> {
                // A fixed all-zero digest plus active=false is a non-credential placeholder.
                // No client secret or webhook signing secret is generated or stored.
                jdbc.sql("""
                        INSERT INTO integration.api_client (
                          id, tenant_id, name, key_prefix, secret_digest, scopes, rate_limit_per_minute, active, created_at)
                        VALUES (:id, :tenant, '演示只读客户端（已吊销）', 'DEMO-REVOKED', decode(repeat('00', 32), 'hex'),
                          '["demo.read"]'::jsonb, 60, FALSE, timestamptz '2026-01-01 00:00:00+00')
                        ON CONFLICT (id) DO NOTHING
                        """).param("id", CLIENT_ID).param("tenant", TENANT_ID).update();
                jdbc.sql("""
                        INSERT INTO integration.api_call_log (
                          id, tenant_id, client_id, scope, api_path, http_method, status, trace_id, created_at)
                        VALUES (:id, :tenant, :client, 'demo.read', '/demo/projects/summary', 'GET', '200',
                          'trace-demo-p5-001', timestamptz '2026-01-02 08:30:00+00')
                        ON CONFLICT (id) DO NOTHING
                        """).param("id", CALL_LOG_ID).param("tenant", TENANT_ID).param("client", CLIENT_ID).update();
                jdbc.sql("""
                        INSERT INTO integration.connector_run (
                          id, tenant_id, connector_code, status, output, error, started_at, finished_at)
                        VALUES (:id, :tenant, 'nexus-self-health', 'SUCCEEDED',
                          '{"status":"UP","service":"nexusone","demo":true}'::jsonb, NULL,
                          timestamptz '2026-01-02 08:29:00+00', timestamptz '2026-01-02 08:29:02+00')
                        ON CONFLICT (id) DO NOTHING
                        """).param("id", RUN_ID).param("tenant", TENANT_ID).update();
            });
        };
    }
}
