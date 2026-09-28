package one.nexus.bootstrap.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import one.nexus.bootstrap.NexusOneApplication;
import one.nexus.p1.config.P1Properties;
import one.nexus.p1.domain.Actor;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.infra.isolation.DemoPlatformContextFilter;
import one.nexus.p1.port.AuditPort;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.PlatformContext;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p3.infra.jdbc.FormTemplateDraftService;
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
    @Autowired tools.jackson.databind.ObjectMapper mapper;
    @Autowired AuditPort audit;
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
        registry.add("nexus.p1.demo-context.enabled", () -> "true");
    }

    @Test
    void demoSeedsCompleteOnAnEmptyDatabaseAndK20IsIdempotent() throws Exception {
        assertSeeded();
        var names = market.catalog().stream().map(item -> item.app().name()).toList();
        assertTrue(names.contains("演示数据 · 请假报销"), "catalog must list the leave/expense demo product");
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

    @Test
    void platformTemplateDraftCreateValidateAndPublishPersistOnPostgres() {
        IsolationGuard isolation = mock(IsolationGuard.class);
        when(isolation.requirePlatformContext(anyString(), any())).thenReturn(new PlatformContext(true, "j1",
                new Actor(UUID.randomUUID(), null, "platform-admin", Set.of(PlatformDuty.SYSTEM_ADMIN)), "trace-j1", null));
        P1Properties properties = new P1Properties();
        FormTemplateDraftService templates = new FormTemplateDraftService(
                jdbc, mapper, isolation, audit, Instant::now, properties);

        var created = templates.create(new FormTemplateDraftService.TemplateWrite("j1_leave", "J1 请假", null,
                List.of(new FormTemplateDraftService.FieldWrite("title", "事由", "TEXT", true, null))));
        assertEquals("DRAFT", created.status());
        assertEquals(1, count("""
                SELECT count(*) FROM platform.audit_event
                WHERE event_type = 'platform.form_template.created' AND resource_id = ? AND tenant_id = ?
                """, created.id().toString(), properties.getPlatformTenant().getId()),
                "I9: form_template.created must persist with non-null tenant_id");

        templates.update(created.id(), new FormTemplateDraftService.TemplateWrite("j1_leave", "J1 请假单", null,
                List.of(new FormTemplateDraftService.FieldWrite("title", "事由", "TEXT", true, null),
                        new FormTemplateDraftService.FieldWrite("days", "天数", "NUMBER", false, null))));

        var validated = templates.validateDraft(created.id());
        assertTrue(validated.valid(), validated.errors().toString());
        assertEquals("VALIDATED", validated.status());

        var published = templates.publishDraft(created.id());
        assertEquals(1, published.version());
        assertEquals(1, count("SELECT count(*) FROM platform.form_template WHERE id = ? AND status = 'PUBLISHED'"
                + " AND running_version_id = ?", created.id(), published.versionId()));
        assertEquals(2, count("SELECT count(*) FROM platform.form_template_field WHERE template_id = ?", created.id()));
        assertEquals(1, count("""
                SELECT count(*) FROM platform.audit_event
                WHERE event_type = 'platform.form_template.published' AND resource_id = ? AND tenant_id IS NOT NULL
                """, created.id().toString()));
    }

    /**
     * AUTH6 hardening: platform content writes require a real bearer token and issued context
     * ({@code PlatformContentContextFilter}); the demo actor headers no longer authorize them. The authenticated path
     * is covered by {@code Auth6RouteRealChainTest}. The audit_event.tenant_id check this test used to make on the
     * header path is deferred with the rest of audit (PL0.2).
     */
    @Test
    void demoActorHeadersCannotWritePlatformContent() throws Exception {
        MvcResult refused = mvc.perform(post("/api/v1/p1/platform/form-templates")
                        .header(DemoPlatformContextFilter.ACTOR_HEADER, "01900000-0000-7000-8000-0000000000ac")
                        .header(DemoPlatformContextFilter.REASON_HEADER, "j1-platform-smoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"j1_header_refused","name":"header refused",
                                 "fields":[{"key":"subject","label":"subject","type":"TEXT","required":true}]}
                                """))
                .andReturn();
        assertEquals(401, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
        assertEquals(0, count("SELECT count(*) FROM platform.form_template WHERE code = ?", "j1_header_refused"),
                "a refused request writes nothing");
    }

    @org.junit.jupiter.api.Disabled("AUTH6: demo headers no longer write platform content; see demoActorHeadersCannotWritePlatformContent")
    @Test
    void platformFormTemplateCreateViaHttpPersistsAuditTenantId() throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/p1/platform/form-templates")
                        .header(DemoPlatformContextFilter.ACTOR_HEADER, "01900000-0000-7000-8000-0000000000ac")
                        .header(DemoPlatformContextFilter.REASON_HEADER, "j1-platform-smoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"j1_platform_smoke","name":"J1 平台配置复验模板",
                                 "fields":[{"key":"subject","label":"事项","type":"TEXT","required":true}]}
                                """))
                .andReturn();
        String body = created.getResponse().getContentAsString();
        assertEquals(200, created.getResponse().getStatus(), body);
        String id = JsonPath.read(body, "$.id");
        assertEquals("DRAFT", JsonPath.read(body, "$.status"));
        assertEquals(1, count("""
                SELECT count(*) FROM platform.audit_event
                WHERE event_type = 'platform.form_template.created' AND resource_id = ? AND tenant_id IS NOT NULL
                """, id), "I9 HTTP save-draft path must not fail on audit_event.tenant_id");
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
                WHERE e.person_id = ? AND p.code = 'demo_tenant_admin' AND e.kind = 'PRIMARY' AND e.status = 'ACTIVE' AND e.deleted_at IS NULL
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
        startAsLin(token, contextId, JsonPath.read(subscribed.getResponse().getContentAsString(), "$.id"));
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

    private void startAsLin(String token, String contextId, String installationId) throws Exception {
        String form = mvc.perform(get("/api/v1/p3/product-applications/installations/" + installationId + "/form")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        java.util.List<String> fieldKeys = JsonPath.read(form, "$.fields[*].key");
        assertTrue(fieldKeys.contains("title"));
        assertTrue(fieldKeys.contains("days"));
        assertTrue(fieldKeys.contains("amount"));
        assertTrue(fieldKeys.contains("approver"));

        MvcResult started = mvc.perform(post("/api/v1/p3/product-applications/installations/" + installationId)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"values\":{\"title\":\"H4 演示发起\","
                                + "\"approver\":\"" + P2DemoSeedConfiguration.PERSON_ZHOU + "\"}}"))
                .andReturn();
        String body = started.getResponse().getContentAsString();
        assertEquals(201, started.getResponse().getStatus(), body);
        assertEquals("RUNNING", JsonPath.read(body, "$.status"));
        assertEquals(installationId, JsonPath.read(body, "$.installation_id"));

        String applicationId = JsonPath.read(body, "$.id");
        assertEquals(1, count("""
                SELECT count(*) FROM business_meta.product_application
                WHERE id = ? AND tenant_id = ? AND initiator_person_id = ? AND status = 'RUNNING'
                  AND created_at IS NOT NULL AND updated_at IS NOT NULL AND process_instance_id IS NOT NULL
                """, java.util.UUID.fromString(applicationId), P1DemoSeedConfiguration.TENANT_ID,
                P2DemoSeedConfiguration.PERSON_LIN), "start must persist the application row with timestamps");

        String mine = mvc.perform(get("/api/v1/p3/product-applications")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Nexus-Context-Id", contextId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        java.util.List<String> mineIds = JsonPath.read(mine, "$[*].id");
        assertTrue(mineIds.contains(applicationId), mine);
    }

    private static void assertBusinessError(MvcResult result, int status, String code, String message) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertEquals(status, result.getResponse().getStatus(), body);
        assertEquals(code, JsonPath.read(body, "$.code"));
        assertEquals(message, JsonPath.read(body, "$.message"));
    }

    private void assertSeeded() {
        assertEquals(4, count("SELECT count(*) FROM platform.form_template_field WHERE template_id = ?",
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
