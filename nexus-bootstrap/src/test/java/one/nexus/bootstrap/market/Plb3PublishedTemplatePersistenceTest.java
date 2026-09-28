package one.nexus.bootstrap.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.bootstrap.NexusOneApplication;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p3.template.PlatformTemplateCatalog;
import one.nexus.p4.market.AppDesignService;
import one.nexus.p4.market.AppDesignService.CategoryWrite;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

/** PLB3 published template snapshots are consumed through P3's port, not mutable platform drafts. */
@SpringBootTest(classes = NexusOneApplication.class)
class Plb3PublishedTemplatePersistenceTest {
    private static EmbeddedPostgres postgres;
    @Autowired JdbcClient jdbc;
    @Autowired AppDesignService design;
    @Autowired PlatformTemplateCatalog templates;
    @MockitoBean IsolationGuard isolation;

    @BeforeAll static void startPostgres() throws Exception { postgres = EmbeddedPostgres.builder().start(); }
    @AfterAll static void stopPostgres() throws Exception { if (postgres != null) postgres.close(); }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("nexus.execution.dispatcher-enabled", () -> "false");
        registry.add("nexus.execution.worker-enabled", () -> "false");
        registry.add("nexus.execution.quartz-auto-startup", () -> "false");
        registry.add("nexus.cache.mode", () -> "memory");
        registry.add("nexus.demo.enabled", () -> "false");
    }

    @Test void draftIsHiddenAndPublishedVersionRemainsPinnedAfterCategoryChanges() {
        UUID appId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        jdbc.sql("INSERT INTO market.app_package(id,code,name,created_at) VALUES (:id,'plb3app','PLB3',now())")
                .param("id", appId).update();
        jdbc.sql("INSERT INTO market.app_version(id,app_id,version,status,created_at) VALUES (:id,:app,'1.0.0','DRAFT',now())")
                .param("id", versionId).param("app", appId).update();
        design.createCategory("oa", new CategoryWrite("办公", null, 0, null));
        design.createCategory("other", new CategoryWrite("其他", null, 1, null));
        design.setAppCategory(appId, "oa");
        Map<String, Object> manifest = Map.of("schema", "nexus.app-manifest.v1", "app_code", "plb3app",
                "version", "1.0.0", "category_code", "oa", "name", "办公应用",
                "ledgers", List.of(Map.of("code", "leave", "name", "请假", "fields", List.of())),
                "forms", List.of(), "processes", List.of(), "role_pack", List.of(), "entries", List.of());
        design.saveManifest(versionId, manifest);
        assertFalse(templates.published(versionId).isPresent());
        design.requirePublishable(versionId);
        jdbc.sql("UPDATE market.app_version SET status='PUBLISHED',published_at=now() WHERE id=:id")
                .param("id", versionId).update();
        var published = templates.published(versionId).orElseThrow();
        assertEquals("oa", published.categoryCode());
        assertEquals("leave", published.ledgers().getFirst().get("code"));
        assertThrows(ResponseStatusException.class, () -> design.saveManifest(versionId, manifest));
        design.setAppCategory(appId, "other");
        assertEquals("oa", templates.published(versionId).orElseThrow().categoryCode());
        jdbc.sql("UPDATE market.app_version SET status='UNPUBLISHED' WHERE id=:id").param("id", versionId).update();
        assertTrue(templates.published(versionId).isPresent()); // installed tenants retain their pinned version
    }
}
