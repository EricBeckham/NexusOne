package one.nexus.bootstrap.demo;

import java.util.UUID;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** Minimal published P4 catalog rows for local screenshots; it does not install or subscribe a tenant. */
@Configuration
public class P4DemoSeedConfiguration {
    static final UUID APP_ID = UUID.fromString("01900000-0000-7000-8000-000000000401");
    static final UUID VERSION_ID = UUID.fromString("01900000-0000-7000-8000-000000000402");
    static final String SKU_CODE = "demo.project-hub.standard";

    @Bean
    ApplicationRunner p4DemoSeed(JdbcClient jdbc, Environment env, TransactionTemplate transaction) {
        return args -> {
            if (!DemoProfileGate.enabled(env.getProperty(DemoProfileGate.ENABLED))) {
                return;
            }
            String datasourceUrl = env.getProperty("spring.datasource.url", "");
            DemoProfileGate.assertAllowed(true, env.getProperty(DemoProfileGate.ENVIRONMENT, "local"), datasourceUrl);

            transaction.executeWithoutResult(status -> {
                jdbc.sql("""
                        INSERT INTO market.app_package (id, code, name, summary, created_at)
                        VALUES (:id, 'demo_project_hub', '星河项目协作', '演示用项目协作应用', timestamptz '2026-01-01 00:00:00+00')
                        ON CONFLICT (id) DO NOTHING
                        """).param("id", APP_ID).update();
                jdbc.sql("""
                        INSERT INTO market.app_version (id, app_id, version, status, release_notes, created_at, published_at)
                        VALUES (:id, :app, '1.0.0', 'PUBLISHED', '演示目录版本',
                          timestamptz '2026-01-01 00:00:00+00', timestamptz '2026-01-02 00:00:00+00')
                        ON CONFLICT (id) DO NOTHING
                        """).param("id", VERSION_ID).param("app", APP_ID).update();
                jdbc.sql("""
                        INSERT INTO market.sku (code, app_id, name, entitlements)
                        VALUES (:code, :app, '标准版',
                          '{"demo":true,"entitlements":{"projects":{"enabled":true,"quota":20}}}'::jsonb)
                        ON CONFLICT (code) DO NOTHING
                        """).param("code", SKU_CODE).param("app", APP_ID).update();
            });
        };
    }
}
