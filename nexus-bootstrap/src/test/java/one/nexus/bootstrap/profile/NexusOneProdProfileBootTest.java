package one.nexus.bootstrap.profile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.charset.StandardCharsets;
import one.nexus.bootstrap.NexusOneApplication;
import one.nexus.crypto.SecretPort;
import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.persist.JdbcIdentityRepository;
import one.nexus.permission.persist.JdbcPolicyRepository;
import one.nexus.permission.persist.PolicyRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Product {@code NexusOneApplication} under the {@code prod} profile against
 * empty PostgreSQL 15: IAM and permission stores on the main database, an env
 * master key, Keycloak IdP, no demo switches, health details not public.
 */
@SpringBootTest(classes = NexusOneApplication.class)
@ActiveProfiles("prod")
class NexusOneProdProfileBootTest {
    /** Test-only key material; never a deployment key. */
    private static final String TEST_MASTER_KEY_HEX =
            "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";
    private static EmbeddedPostgres postgres;

    @Autowired ProductionProfileConfiguration.ProductionProfileCheck check;
    @Autowired IdentityRepository identities;
    @Autowired PolicyRepository policies;
    @Autowired SecretPort secrets;
    @Autowired Environment env;

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
        registry.add("nexus.crypto.master-key", () -> TEST_MASTER_KEY_HEX);
        registry.add("nexus.iam.keycloak.issuer", () -> "https://id.example.invalid/realms/nexus");
        registry.add("nexus.iam.keycloak.client-id", () -> "nexus-platform");
        registry.add("nexus.iam.keycloak.client-secret", () -> "test-only");
        registry.add("nexus.iam.keycloak.redirect-uri", () -> "https://app.example.invalid/api/v1/auth/callback");
        registry.add("nexus.execution.dispatcher-enabled", () -> "false");
        registry.add("nexus.execution.worker-enabled", () -> "false");
        registry.add("nexus.execution.quartz-auto-startup", () -> "false");
        registry.add("nexus.cache.mode", () -> "memory");
    }

    @Test
    void prodProfileBootsWithDurableStoresAndNoDemoSwitches() {
        assertTrue(check.production());
        assertTrue(ProductionProfileGuard.violations(env).isEmpty());
        assertTrue(identities instanceof JdbcIdentityRepository, identities.getClass().getName());
        assertTrue(policies instanceof JdbcPolicyRepository, policies.getClass().getName());
        assertEquals("false", env.getProperty("nexus.demo.enabled"));
        assertEquals("when-authorized", env.getProperty("management.endpoint.health.show-details"));
        assertEquals("false", env.getProperty("management.info.env.enabled"));
    }

    @Test
    void secretsRoundTripUnderTheEnvMasterKey() {
        byte[] value = "prod-profile-boot".getBytes(StandardCharsets.UTF_8);
        secrets.put("l12.prod-profile.check", value);
        assertArrayEquals(value, secrets.get("l12.prod-profile.check"));
        secrets.delete("l12.prod-profile.check");
    }
}
