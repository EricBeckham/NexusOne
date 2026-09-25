package one.nexus.bootstrap.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import one.nexus.bootstrap.NexusOneApplication;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p4.market.MarketService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * P1: empty PostgreSQL 15 + {@code nexus.demo.enabled=true} boots the product with every demo
 * seed (P1–P5, K20) and the K20 seed can run again without duplicating form template fields.
 */
@SpringBootTest(classes = NexusOneApplication.class)
@AutoConfigureMockMvc
class DemoSeedPostgresBootTest {
    private static EmbeddedPostgres postgres;

    @Autowired JdbcTemplate jdbc;
    @Autowired MarketService market;
    @Autowired MockMvc mvc;
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
        registry.add("nexus.iam.persistence", () -> "jdbc");
        registry.add("nexus.iam.jdbc.migrate", () -> "false");
        registry.add("nexus.iam.jdbc.url",
                () -> "jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres");
        registry.add("nexus.iam.jdbc.username", () -> "postgres");
        registry.add("nexus.iam.jdbc.password", () -> "postgres");
    }

    @Test
    void demoSeedsCompleteOnAnEmptyDatabaseAndK20IsIdempotent() throws Exception {
        assertSeeded();
        var names = market.catalog().stream().map(item -> item.app().name()).toList();
        assertTrue(names.contains("演示数据 · 简单审批"), "catalog must list the simple-approval demo product");
        assertTrue(names.contains("星河项目协作"), "catalog must list the P4 demo app");
        assertCatalogSkuContract();
        int quotaUsed = count("SELECT coalesce(sum(used_value), 0) FROM platform.quota WHERE tenant_id = ?",
                P1DemoSeedConfiguration.TENANT_ID);
        approvalSeed.run(new DefaultApplicationArguments());
        approvalSeed.run(new DefaultApplicationArguments());
        p3Seed.run(new DefaultApplicationArguments());
        assertSeeded();
        assertEquals(quotaUsed, count("SELECT coalesce(sum(used_value), 0) FROM platform.quota WHERE tenant_id = ?",
                P1DemoSeedConfiguration.TENANT_ID), "re-running the P3 seed must not consume quota twice");
        subscribeAsLin();
    }

    private void assertCatalogSkuContract() throws Exception {
        String catalog = mvc.perform(get("/api/v1/p4/catalog"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        java.util.List<String> codes = JsonPath.read(catalog, "$..skus[*].code");
        java.util.List<String> names = JsonPath.read(catalog, "$..skus[*].name");
        assertTrue(codes.contains(ApprovalProductDemoSeedConfiguration.SKU_CODE));
        assertTrue(names.contains("演示数据 · 标准"));
    }

    private void subscribeAsLin() throws Exception {
        assertEquals(1, count("""
                SELECT count(*) FROM iam.iam_employment e
                JOIN iam.iam_position p ON p.id = e.position_id
                WHERE e.person_id = ? AND p.code = 'TENANT_ADMIN' AND e.status = 'ACTIVE' AND e.deleted_at IS NULL
                """, P2DemoSeedConfiguration.PERSON_LIN));
        String token = StubIdentityProvider.token(
                "demo-seed:p2:lin", "lin.xiao@demo.invalid", "demo-lin", "林晓");
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"access_token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String session = login.getResponse().getContentAsString();
        String tenantId = JsonPath.read(session, "$.tenant_id");
        String contextId = JsonPath.read(session, "$.context_id");
        assertEquals(P1DemoSeedConfiguration.TENANT_ID.toString(), tenantId);

        jdbc.update("DELETE FROM market.sku_product_binding WHERE sku_code = ?",
                ApprovalProductDemoSeedConfiguration.SKU_CODE);
        assertBusinessError(subscribe(token, tenantId, contextId), 404,
                "P4.MARKET.0001", "catalog SKU has no product definition");
        approvalSeed.run(new DefaultApplicationArguments());

        MvcResult subscribed = subscribe(token, tenantId, contextId);
        assertEquals(201, subscribed.getResponse().getStatus(), subscribed.getResponse().getContentAsString());
        assertBusinessError(subscribe(token, tenantId, contextId), 409,
                "P4.MARKET.0003", "tenant already has an active subscription for this SKU");

        String zhouToken = StubIdentityProvider.token(
                "demo-seed:p2:zhou", "zhou.ran@demo.invalid", "demo-zhou", "周然");
        String zhouSession = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"access_token\":\"" + zhouToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertBusinessError(subscribe(zhouToken, tenantId, JsonPath.read(zhouSession, "$.context_id")), 403,
                "P4.MARKET.0002", "tenant administrator role is required");
    }

    private MvcResult subscribe(String token, String tenantId, String contextId) throws Exception {
        return mvc.perform(post("/api/v1/p4/tenants/" + tenantId + "/subscriptions")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku_code\":\"" + ApprovalProductDemoSeedConfiguration.SKU_CODE
                                + "\",\"version_id\":\"" + ApprovalProductDemoSeedConfiguration.APP_VERSION_ID + "\"}"))
                .andReturn();
    }

    private static void assertBusinessError(MvcResult result, int status, String code, String message) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertEquals(status, result.getResponse().getStatus(), body);
        assertEquals(code, JsonPath.read(body, "$.code"));
        assertEquals(message, JsonPath.read(body, "$.message"));
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
        assertEquals(ApprovalProductDemoSeedConfiguration.DEFINITION_ID,
                jdbc.queryForObject("""
                        SELECT product_definition_id FROM market.sku_product_binding
                        WHERE sku_code = ?
                        """, java.util.UUID.class, ApprovalProductDemoSeedConfiguration.SKU_CODE));
        assertEquals(1, count("SELECT count(*) FROM market.product_definition WHERE id = ? AND status = 'PUBLISHED'",
                ApprovalProductDemoSeedConfiguration.DEFINITION_ID));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }
}
