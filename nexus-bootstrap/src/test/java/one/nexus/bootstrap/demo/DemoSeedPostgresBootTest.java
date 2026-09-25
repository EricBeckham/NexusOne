package one.nexus.bootstrap.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import one.nexus.bootstrap.NexusOneApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * P1: empty PostgreSQL 15 + {@code nexus.demo.enabled=true} boots the product with every demo
 * seed (P1–P5, K20) and the K20 seed can run again without duplicating form template fields.
 */
@SpringBootTest(classes = NexusOneApplication.class)
class DemoSeedPostgresBootTest {
    private static EmbeddedPostgres postgres;

    @Autowired JdbcTemplate jdbc;
    @Autowired @Qualifier("approvalProductDemoSeed") ApplicationRunner approvalSeed;
    @Autowired @Qualifier("p3DemoSeed") ApplicationRunner p3Seed;

    @BeforeAll
    static void startPostgres() throws Exception {
        postgres = EmbeddedPostgres.builder().start();
    }

    @AfterAll
    static void stopPostgres() throws Exception {
        if (postgres != null) postgres.close();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("nexus.demo.enabled", () -> "true");
        registry.add("nexus.demo.environment", () -> "local");
        registry.add("nexus.execution.dispatcher-enabled", () -> "false");
        registry.add("nexus.execution.worker-enabled", () -> "false");
        registry.add("nexus.execution.quartz-auto-startup", () -> "false");
        registry.add("nexus.cache.mode", () -> "memory");
    }

    @Test
    void demoSeedsCompleteOnAnEmptyDatabaseAndK20IsIdempotent() throws Exception {
        assertSeeded();
        int quotaUsed = count("SELECT coalesce(sum(used_value), 0) FROM platform.quota WHERE tenant_id = ?",
                P1DemoSeedConfiguration.TENANT_ID);
        approvalSeed.run(new DefaultApplicationArguments());
        approvalSeed.run(new DefaultApplicationArguments());
        p3Seed.run(new DefaultApplicationArguments());
        assertSeeded();
        assertEquals(quotaUsed, count("SELECT coalesce(sum(used_value), 0) FROM platform.quota WHERE tenant_id = ?",
                P1DemoSeedConfiguration.TENANT_ID), "re-running the P3 seed must not consume quota twice");
    }

    private void assertSeeded() {
        assertEquals(2, count("SELECT count(*) FROM platform.form_template_field WHERE template_id = ?",
                ApprovalProductDemoSeedConfiguration.TEMPLATE_ID));
        assertEquals(1, count("SELECT count(*) FROM platform.form_template_field"
                + " WHERE template_id = ? AND field_key = 'approver' AND field_type = 'PERSON'",
                ApprovalProductDemoSeedConfiguration.TEMPLATE_ID));
        assertEquals(1, count("SELECT count(*) FROM platform.form_template WHERE id = ? AND running_version_id = ?",
                ApprovalProductDemoSeedConfiguration.TEMPLATE_ID, ApprovalProductDemoSeedConfiguration.TEMPLATE_VERSION_ID));
        assertEquals(1, count("SELECT count(*) FROM market.sku_product_binding WHERE sku_code = ?",
                ApprovalProductDemoSeedConfiguration.SKU_CODE));
        assertEquals(1, count("SELECT count(*) FROM market.product_definition WHERE id = ? AND status = 'PUBLISHED'",
                ApprovalProductDemoSeedConfiguration.DEFINITION_ID));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }
}
