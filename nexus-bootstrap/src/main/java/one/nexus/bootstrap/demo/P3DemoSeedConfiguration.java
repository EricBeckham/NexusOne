package one.nexus.bootstrap.demo;

import java.util.UUID;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import one.nexus.p3.port.QuotaHook;

/** Minimal P3 application and draft-ledger metadata for local screenshots; no physical table is created. */
@Configuration
public class P3DemoSeedConfiguration {
    static final UUID APPLICATION_ID = UUID.fromString("01900000-0000-7000-8000-000000000301");
    static final UUID LEDGER_ID = UUID.fromString("01900000-0000-7000-8000-000000000302");
    static final UUID NAME_FIELD_ID = UUID.fromString("01900000-0000-7000-8000-000000000303");
    static final UUID STATUS_FIELD_ID = UUID.fromString("01900000-0000-7000-8000-000000000304");
    static final UUID TENANT_ID = P1DemoSeedConfiguration.TENANT_ID;
    static final String PHYSICAL_TABLE = "l_demo_acme_demo_studio_project";

    @Bean
    ApplicationRunner p3DemoSeed(JdbcClient jdbc, Environment env, TransactionTemplate transaction, QuotaHook quotas) {
        return args -> {
            if (!DemoProfileGate.enabled(env.getProperty(DemoProfileGate.ENABLED))) {
                return;
            }
            String datasourceUrl = env.getProperty("spring.datasource.url", "");
            DemoProfileGate.assertAllowed(true, env.getProperty(DemoProfileGate.ENVIRONMENT, "local"), datasourceUrl);

            // Ensure the fixed tenant and its quota rows exist even if runner order differs.
            new P1DemoSeedConfiguration().p1DemoSeed(jdbc, env, transaction).run(args);

            transaction.executeWithoutResult(status -> {
                jdbc.sql("""
                        INSERT INTO business_meta.application (
                          id, tenant_id, tenant_code, app_code, display_name, kind, status,
                          created_at, created_by, updated_at, updated_by, deleted_at, version)
                        VALUES (:id, :tenant, 'demo_acme', 'demo_studio', '演示工作台', 'CUSTOM', 'ACTIVE',
                          timestamptz '2026-01-01 00:00:00+00', 'demo-seed-p3',
                          timestamptz '2026-01-01 00:00:00+00', 'demo-seed-p3', NULL, 1)
                        ON CONFLICT (id) DO NOTHING
                        """).param("id", APPLICATION_ID).param("tenant", TENANT_ID).update();
                jdbc.sql("""
                        INSERT INTO business_meta.ledger (
                          id, tenant_id, tenant_code, application_id, ledger_code, display_name,
                          scope, physical_table, schema_version, status, created_at, created_by,
                          updated_at, updated_by, deleted_at, version)
                        VALUES (:id, :tenant, 'demo_acme', :application, 'project', '演示项目台账',
                          'TENANT', :physical_table, 1, 'DRAFT', timestamptz '2026-01-01 00:00:00+00',
                          'demo-seed-p3', timestamptz '2026-01-01 00:00:00+00', 'demo-seed-p3', NULL, 1)
                        ON CONFLICT (id) DO NOTHING
                        """).param("id", LEDGER_ID).param("tenant", TENANT_ID)
                        .param("application", APPLICATION_ID).param("physical_table", PHYSICAL_TABLE).update();
                jdbc.sql("""
                        INSERT INTO business_meta.ledger_field (
                          id, tenant_id, ledger_id, field_code, display_name, field_type, required, indexed,
                          sort_order, created_at, created_by, updated_at, updated_by, deleted_at, version)
                        VALUES (:id, :tenant, :ledger, 'name', '项目名称', 'TEXT', TRUE, FALSE, 0,
                          timestamptz '2026-01-01 00:00:00+00', 'demo-seed-p3',
                          timestamptz '2026-01-01 00:00:00+00', 'demo-seed-p3', NULL, 1)
                        ON CONFLICT (id) DO NOTHING
                        """).param("id", NAME_FIELD_ID).param("tenant", TENANT_ID)
                        .param("ledger", LEDGER_ID).update();
                jdbc.sql("""
                        INSERT INTO business_meta.ledger_field (
                          id, tenant_id, ledger_id, field_code, display_name, field_type, required, indexed,
                          sort_order, created_at, created_by, updated_at, updated_by, deleted_at, version)
                        VALUES (:id, :tenant, :ledger, 'status', '项目状态', 'TEXT', FALSE, FALSE, 1,
                          timestamptz '2026-01-01 00:00:00+00', 'demo-seed-p3',
                          timestamptz '2026-01-01 00:00:00+00', 'demo-seed-p3', NULL, 1)
                        ON CONFLICT (id) DO NOTHING
                        """).param("id", STATUS_FIELD_ID).param("tenant", TENANT_ID)
                        .param("ledger", LEDGER_ID).update();
                quotas.consumeApp(TENANT_ID, "p3.app:" + TENANT_ID + ":demo_studio");
                quotas.consumeCustomLedger(TENANT_ID, "p3.ledger:" + TENANT_ID + ":project");
            });
        };
    }
}
