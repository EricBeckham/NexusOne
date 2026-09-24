package one.nexus.bootstrap.demo;

import java.util.UUID;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** Minimal P1 visual-demo tenant. It is inert unless the explicitly guarded demo switch is on. */
@Configuration
public class P1DemoSeedConfiguration {
    static final UUID TENANT_ID = UUID.fromString("01900000-0000-7000-8000-000000000101");
    static final UUID PLATFORM_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");

    @Bean
    ApplicationRunner p1DemoSeed(JdbcClient jdbc, Environment env, TransactionTemplate transaction) {
        return args -> {
            boolean enabled = DemoProfileGate.enabled(env.getProperty(DemoProfileGate.ENABLED));
            if (!enabled) {
                return;
            }
            String environment = env.getProperty(DemoProfileGate.ENVIRONMENT, "local");
            String datasourceUrl = env.getProperty("spring.datasource.url", "");
            DemoProfileGate.assertAllowed(true, environment, datasourceUrl);
            if (datasourceUrl.isBlank()) {
                throw new IllegalStateException("禁止连生产：演示数据需要明确配置本机数据库地址");
            }

            transaction.executeWithoutResult(status -> {
                jdbc.sql("""
                        INSERT INTO platform.tenant (
                          id, code, name, kind, status, legal_name, created_at, created_by,
                          updated_at, updated_by, deleted_at, version)
                        VALUES (:id, 'demo_acme', '演示租户 · 星河科技', 'BUSINESS', 'ENABLED',
                          '星河科技（演示）', timestamptz '2026-01-01 00:00:00+00', :actor,
                          timestamptz '2026-01-01 00:00:00+00', :actor, NULL, 1)
                        ON CONFLICT (id) DO NOTHING
                        """).param("id", TENANT_ID).param("actor", PLATFORM_ID).update();
                jdbc.sql("""
                        INSERT INTO platform.tenant_config (
                          tenant_id, display_name, locale, timezone, date_format, currency,
                          fiscal_year_start_month, logo_file_id, favicon_file_id, updated_at, updated_by, version)
                        VALUES (:id, '星河科技（演示）', 'zh-CN', 'Asia/Shanghai', 'yyyy-MM-dd', 'CNY',
                          1, NULL, NULL, timestamptz '2026-01-01 00:00:00+00', :actor, 1)
                        ON CONFLICT (tenant_id) DO NOTHING
                        """).param("id", TENANT_ID).param("actor", PLATFORM_ID).update();
                jdbc.sql("""
                        INSERT INTO platform.entitlement_projection (
                          tenant_id, source, pack_code, pack_version, sku_code, subscription_id,
                          projected_at, payload, updated_at, version)
                        VALUES (:id, 'DEFAULT_PACK', 'demo.standard', '1', NULL, NULL,
                          timestamptz '2026-01-01 00:00:00+00',
                          '{"schema":"p4.runtime-entitlement.v1","demo":true,"entitlements":{"apps":{"enabled":true,"quota":5},"custom_ledgers":{"enabled":true,"quota":10}}}'::jsonb,
                          timestamptz '2026-01-01 00:00:00+00', 1)
                        ON CONFLICT (tenant_id) DO NOTHING
                        """).param("id", TENANT_ID).update();
                jdbc.sql("""
                        INSERT INTO platform.quota (
                          tenant_id, metric, limit_value, used_value, warn_percent, over_limit_policy, updated_at, version)
                        VALUES (:id, 'APP', 5, 0, 80, 'REJECT', timestamptz '2026-01-01 00:00:00+00', 1),
                               (:id, 'CUSTOM_LEDGER', 10, 0, 80, 'REJECT', timestamptz '2026-01-01 00:00:00+00', 1)
                        ON CONFLICT (tenant_id, metric) DO NOTHING
                        """).param("id", TENANT_ID).update();
            });
        };
    }
}
