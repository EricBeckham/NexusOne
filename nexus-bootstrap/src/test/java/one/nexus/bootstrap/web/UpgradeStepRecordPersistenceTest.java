package one.nexus.bootstrap.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import one.nexus.bootstrap.NexusOneApplication;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p4.market.MarketIsolation;
import one.nexus.p4.market.MarketModels.UpgradeStepRecord;
import one.nexus.p4.market.MarketModels.UpgradeStepStatus;
import one.nexus.p4.market.MarketRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * DPO-L6: operator records land in market.upgrade_step_record (V709); the tenant read sees only its own
 * installation's records for the requested version; the table refuses UNKNOWN and a blank reason.
 */
@SpringBootTest(classes = NexusOneApplication.class)
class UpgradeStepRecordPersistenceTest {
    private static final UUID PLATFORM_TENANT = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static EmbeddedPostgres postgres;

    @Autowired MarketRepository market;
    @Autowired JdbcClient jdbc;
    @Autowired @Qualifier("marketIsolation") MarketIsolation isolation;

    @BeforeAll
    static void startPostgres() throws Exception {
        postgres = EmbeddedPostgres.builder().start();
    }

    @AfterAll
    static void stopPostgres() throws Exception {
        if (postgres != null) postgres.close();
    }

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
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

    private <T> T as(UUID tenant, java.util.concurrent.Callable<T> call) {
        var context = isolation.kernel().tenants().issue(new AuthenticatedMembership(Actor.person("dpo-l6"),
                tenant.toString(), tenant.toString(), Set.of(tenant.toString())), "dpo-l6-trace");
        return AccessContextHolder.call(context, call);
    }

    @Test
    void recordsAreStoredAndReadOnlyThroughTheOwningTenantAndVersion() throws Exception {
        UUID app = UUID.randomUUID();
        UUID v1 = UUID.randomUUID();
        UUID v2 = UUID.randomUUID();
        UUID installation = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.parse("2026-09-27T09:00:00Z"));
        jdbc.sql("INSERT INTO market.app_package(id,code,name,created_at) VALUES(:id,:code,'app',:now)")
                .param("id", app).param("code", "dpo-l6-" + app).param("now", now).update();
        for (UUID version : List.of(v1, v2)) {
            jdbc.sql("INSERT INTO market.app_version(id,app_id,version,status,created_at,published_at) VALUES(:id,:app,:v,'PUBLISHED',:now,:now)")
                    .param("id", version).param("app", app).param("v", version.toString().substring(0, 8)).param("now", now).update();
        }
        jdbc.sql("INSERT INTO market.installation(id,tenant_id,app_id,version_id,status,installed_at,updated_at) VALUES(:id,:t,:app,:v,'INSTALLED',:now,:now)")
                .param("id", installation).param("t", PLATFORM_TENANT).param("app", app).param("v", v2).param("now", now).update();

        assertEquals(v2, market.installationForPlatform(installation).orElseThrow().versionId());
        UUID operator = UUID.randomUUID();
        market.saveUpgradeStepRecord(new UpgradeStepRecord(UUID.randomUUID(), PLATFORM_TENANT, installation, v1, "verify",
                UpgradeStepStatus.DONE, Instant.parse("2026-09-26T00:00:00Z"), operator, "earlier upgrade", null));
        market.saveUpgradeStepRecord(new UpgradeStepRecord(UUID.randomUUID(), PLATFORM_TENANT, installation, v2, "precheck",
                UpgradeStepStatus.DONE, Instant.parse("2026-09-27T09:05:00Z"), operator, "prechecked by ops", "ticket 42"));

        List<UpgradeStepRecord> own = as(PLATFORM_TENANT, () -> market.upgradeStepRecords(installation, v2));
        assertEquals(1, own.size());
        assertEquals("precheck", own.get(0).stepKey());
        assertEquals("ticket 42", own.get(0).note());
        assertEquals(operator, own.get(0).recordedBy());
        assertEquals(1, as(PLATFORM_TENANT, () -> market.upgradeStepRecords(installation, v1)).size());
        assertTrue(as(UUID.randomUUID(), () -> market.upgradeStepRecords(installation, v2)).isEmpty(),
                "another tenant reads nothing");

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql("""
                INSERT INTO market.upgrade_step_record(id,tenant_id,installation_id,version_id,step_key,status,recorded_at,recorded_by,reason)
                VALUES(:id,:t,:i,:v,'verify','UNKNOWN',now(),:op,'x')
                """).param("id", UUID.randomUUID()).param("t", PLATFORM_TENANT).param("i", installation).param("v", v2)
                .param("op", operator).update());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql("""
                INSERT INTO market.upgrade_step_record(id,tenant_id,installation_id,version_id,step_key,status,recorded_at,recorded_by,reason)
                VALUES(:id,:t,:i,:v,'verify','DONE',now(),:op,'   ')
                """).param("id", UUID.randomUUID()).param("t", PLATFORM_TENANT).param("i", installation).param("v", v2)
                .param("op", operator).update());
    }
}
