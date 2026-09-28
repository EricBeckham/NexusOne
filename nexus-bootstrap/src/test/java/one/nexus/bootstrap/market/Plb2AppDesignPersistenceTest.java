package one.nexus.bootstrap.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.bootstrap.NexusOneApplication;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p4.market.AppDesignService;
import one.nexus.p4.market.AppDesignService.CategoryWrite;
import one.nexus.p4.market.AppDesignService.ManifestValidationException;
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

/** PLB2 writes only to this test's disposable loopback PostgreSQL. */
@SpringBootTest(classes = NexusOneApplication.class)
class Plb2AppDesignPersistenceTest {
    private static EmbeddedPostgres postgres;
    @Autowired JdbcClient jdbc;
    @Autowired AppDesignService design;
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

    @Test void categoryAndManifestWritesAreGuardedValidatedAndDraftOnly() {
        UUID appId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        jdbc.sql("INSERT INTO market.app_package(id,code,name,created_at) VALUES (:id,'plb2app','PLB2',now())")
                .param("id", appId).update();
        jdbc.sql("INSERT INTO market.app_version(id,app_id,version,status,created_at) VALUES (:id,:app,'1.0.0','DRAFT',now())")
                .param("id", versionId).param("app", appId).update();

        var category = design.createCategory("oa", new CategoryWrite("办公", "Office", 10, null));
        assertEquals(1, category.version());
        assertEquals("oa", design.category("oa").code());
        assertEquals(1, design.categories().size());
        assertThrows(ResponseStatusException.class,
                () -> design.updateCategory("oa", new CategoryWrite("旧请求", null, 1, 9L)));
        assertEquals(2, design.updateCategory("oa", new CategoryWrite("办公应用", null, 5, 1L)).version());
        design.setAppCategory(appId, "oa");
        assertThrows(ResponseStatusException.class, () -> design.deleteCategory("oa"));
        assertThrows(ResponseStatusException.class, () -> design.requirePublishable(versionId));

        Map<String, Object> manifest = Map.of("schema", "nexus.app-manifest.v1", "app_code", "plb2app",
                "version", "1.0.0", "category_code", "oa", "name", "办公应用",
                "ledgers", List.of(), "forms", List.of(), "processes", List.of(),
                "role_pack", List.of(), "entries", List.of());
        assertTrue(design.validate(versionId, manifest).valid());
        assertEquals(manifest, design.saveManifest(versionId, manifest).manifest());
        assertEquals(manifest, design.manifest(versionId).manifest());
        design.requirePublishable(versionId);
        assertThrows(ManifestValidationException.class,
                () -> design.saveManifest(versionId, Map.of("schema", "nexus.app-manifest.v1")));

        jdbc.sql("UPDATE market.app_version SET status='PUBLISHED' WHERE id=:id").param("id", versionId).update();
        assertThrows(ResponseStatusException.class, () -> design.saveManifest(versionId, manifest));
        verify(isolation, org.mockito.Mockito.atLeastOnce()).requirePlatformContext(anyString(), any());
    }
}
