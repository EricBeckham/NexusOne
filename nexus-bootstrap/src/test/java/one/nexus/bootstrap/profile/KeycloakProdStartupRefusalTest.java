package one.nexus.bootstrap.profile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import one.nexus.bootstrap.NexusOneApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * DL-L5: the real application under {@code prod} with one Keycloak variable missing refuses to start. Everything it
 * prints — the Spring failure report and every log line — names the variable and carries none of the configured
 * values (secret, client id, issuer host).
 */
@ExtendWith(OutputCaptureExtension.class)
class KeycloakProdStartupRefusalTest {
    private static final String SECRET = "dl-l5-boot-secret-4f7d2b";
    private static final String CLIENT = "dl-l5-boot-client-31";
    private static final String HOST = "dl-l5-boot-idp.example.invalid";
    /** Test-only key material; never a deployment key. */
    private static final String TEST_MASTER_KEY_HEX = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";
    private static EmbeddedPostgres postgres;

    @BeforeAll
    static void startPostgres() throws Exception {
        postgres = EmbeddedPostgres.builder().start();
    }

    @AfterAll
    static void stopPostgres() throws Exception {
        if (postgres != null) postgres.close();
    }

    @Test
    void prodRefusesToStartAndPrintsOnlyTheVariableName(CapturedOutput output) {
        // Command-line arguments outrank application*.yaml placeholders, like real environment variables do.
        String[] args = {
                "--spring.datasource.url=jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres",
                "--spring.datasource.username=postgres",
                "--spring.datasource.password=postgres",
                "--nexus.crypto.master-key=" + TEST_MASTER_KEY_HEX,
                "--nexus.iam.keycloak.issuer=https://" + HOST + "/realms/nexus",
                "--nexus.iam.keycloak.client-id=" + CLIENT,
                "--nexus.iam.keycloak.client-secret=" + SECRET,
                // missing on purpose: only the variable name NEXUS_IAM_KEYCLOAK_REDIRECT_URI may appear
                "--nexus.iam.keycloak.redirect-uri=",
                "--nexus.execution.dispatcher-enabled=false",
                "--nexus.execution.worker-enabled=false",
                "--nexus.execution.quartz-auto-startup=false",
                "--nexus.cache.mode=memory",
                "--server.port=0"};
        SpringApplicationBuilder app = new SpringApplicationBuilder(NexusOneApplication.class).profiles("prod");
        Throwable failure = assertThrows(Throwable.class, () -> app.run(args));
        String everything = output.getAll() + "\n" + stack(failure);
        assertTrue(everything.contains("NEXUS_IAM_KEYCLOAK_REDIRECT_URI"), "the missing variable must be named");
        assertTrue(everything.contains("prod profile refused to start"), "the refusal must be visible");
        assertFalse(everything.contains(SECRET), "client secret leaked");
        assertFalse(everything.contains(CLIENT), "client id leaked");
        assertFalse(everything.contains(HOST), "issuer host leaked");
        assertFalse(everything.contains(TEST_MASTER_KEY_HEX), "master key leaked");
    }

    private static String stack(Throwable failure) {
        StringBuilder text = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) text.append(t).append('\n');
        return text.toString();
    }
}
