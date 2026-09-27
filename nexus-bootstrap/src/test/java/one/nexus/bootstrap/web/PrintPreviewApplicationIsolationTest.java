package one.nexus.bootstrap.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import one.nexus.bootstrap.NexusOneApplication;
import one.nexus.p3.port.ProductApplicationStore;
import one.nexus.p3.port.ProductApplicationStore.ApplicationSnapshot;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** DPO-L5: the print preview reads an application only through the owning tenant, against the real table. */
@SpringBootTest(classes = NexusOneApplication.class)
class PrintPreviewApplicationIsolationTest {
    private static EmbeddedPostgres postgres;

    @Autowired ProductApplicationStore applications;
    @Autowired JdbcClient jdbc;

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
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("nexus.execution.dispatcher-enabled", () -> "false");
        registry.add("nexus.execution.worker-enabled", () -> "false");
        registry.add("nexus.execution.quartz-auto-startup", () -> "false");
        registry.add("nexus.cache.mode", () -> "memory");
    }

    @Test
    void onlyTheOwningTenantCanReadTheApplication() {
        UUID alpha = UUID.randomUUID();
        UUID beta = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        Timestamp created = Timestamp.from(Instant.parse("2026-09-27T08:00:00Z"));
        jdbc.sql("""
                INSERT INTO business_meta.product_application(id,tenant_id,installation_id,product_definition_id,
                    form_template_version_id,values_json,status,initiator_person_id,created_at,updated_at)
                VALUES(:id,:tenant,NULL,:product,:version,'{"title":"年假"}'::jsonb,'RUNNING',:person,:at,:at)
                """).param("id", id).param("tenant", alpha).param("product", product).param("version", version)
                .param("person", UUID.randomUUID()).param("at", created).update();

        ApplicationSnapshot own = applications.findForTenant(alpha, id).orElseThrow();
        assertEquals(product, own.productDefinitionId());
        assertEquals(version, own.formTemplateVersionId());
        assertTrue(own.valuesJson().contains("年假"), own.valuesJson());
        assertEquals(Instant.parse("2026-09-27T08:00:00Z"), own.createdAt());
        assertTrue(applications.findForTenant(beta, id).isEmpty(), "another tenant must not see it");
        assertTrue(applications.findForTenant(alpha, UUID.randomUUID()).isEmpty());
    }
}
