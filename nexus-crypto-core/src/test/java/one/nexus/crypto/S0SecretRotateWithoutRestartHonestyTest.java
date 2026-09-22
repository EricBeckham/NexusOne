package one.nexus.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import one.nexus.crypto.S0SecretRotateWithoutRestartHonesty.EnvMapRotate;
import one.nexus.crypto.S0SecretRotateWithoutRestartHonesty.OsGetenvInventory;
import org.junit.jupiter.api.Test;

/**
 * S0 leftover honesty beyond #143 / #180: mutable env-map rotate stays
 * always-green; OS {@code System.getenv()} startup snapshot is honest SKIP.
 * Does not redo the #143 suite or the #180 leftover. Does not introduce
 * OpenBao / KMS. Does not claim product S0 / SecretPort close.
 */
class S0SecretRotateWithoutRestartHonestyTest {

    @Test
    void envMapRotateIsPickedUpWithoutRedoing143Or180() {
        EnvMapRotate rotate = S0SecretRotateWithoutRestartHonesty.probeEnvMapRotate();
        assertTrue(rotate.pickedUp());
        assertEquals("PASS", rotate.verdict());
    }

    @Test
    void osGetenvProbeIsHonestSkipOnJava21Snapshot() {
        OsGetenvInventory os = S0SecretRotateWithoutRestartHonesty.probeOsGetenv();
        assertEquals("SKIP", os.osGetenvRotate());
        assertFalse(os.skipReason().isBlank());
        assertTrue(os.getenvUnmodifiable() || os.getenvMutable());
        assertFalse(os.osRotateObserved());
        assertTrue(os.skipReason().contains("snapshot")
                || os.skipReason().contains("unmodifiable")
                || os.skipReason().contains("not observed"));
        assertTrue(os.skipReason().contains("System.getenv")
                || os.skipReason().contains("OS env"));
        assertTrue(os.skipReason().contains("#143") || os.skipReason().contains("#180"));
    }

    @Test
    void honestyGateAcceptsHonestSkipTemplate() {
        EnvMapRotate envMap = S0SecretRotateWithoutRestartHonesty.probeEnvMapRotate();
        OsGetenvInventory os = S0SecretRotateWithoutRestartHonesty.probeOsGetenv();
        Map<String, String> report = S0SecretRotateWithoutRestartHonesty.honestSkipTemplate(envMap, os);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        S0SecretRotateWithoutRestartHonesty.verify(report);
        assertEquals("PASS", report.get("env_map_rotate"));
        assertEquals("SKIP", report.get("os_getenv_rotate"));
        assertEquals(S0SecretRotateWithoutRestartHonesty.S0_CLOSE, report.get("s0_close"));
        assertEquals(S0SecretRotateWithoutRestartHonesty.SECRET_PORT_CLOSE, report.get("secret_port_close"));
        assertEquals("false", report.get("redo_143"));
        assertEquals("false", report.get("redo_180"));
        assertEquals("false", report.get("openbao_introduced"));
        assertEquals("false", report.get("kms_introduced"));
        assertEquals("false", report.get("p0_m0_redone"));
        assertEquals("closed_unchanged", report.get("g18_status"));
        assertTrue(report.get("leftover_rows_kept").contains("s0-secret-rotate-without-restart"));
        assertTrue(report.get("leftover_rows_kept").contains("s0-secret-getenv-snapshot-honesty"));
        S0SecretRotateWithoutRestartHonesty.verify(
                S0SecretRotateWithoutRestartHonesty.parseReport(fixture("honest-skip.env")));
    }

    @Test
    void honestyGateRejectsFakeOsGetenvPass() {
        Map<String, String> fake = new HashMap<>(
                S0SecretRotateWithoutRestartHonesty.parseReport(fixture("fake-os-getenv-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> S0SecretRotateWithoutRestartHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("os_getenv"));
    }

    @Test
    void honestyGateRejectsS0AndSecretPortCloseAndOpenBao() {
        Map<String, String> fake = new HashMap<>(
                S0SecretRotateWithoutRestartHonesty.parseReport(fixture("honest-skip.env")));
        fake.put("s0_close", "CLOSED");
        IllegalArgumentException close = assertThrows(IllegalArgumentException.class,
                () -> S0SecretRotateWithoutRestartHonesty.verify(fake));
        assertTrue(close.getMessage().contains("s0_close"));

        fake.put("s0_close", S0SecretRotateWithoutRestartHonesty.S0_CLOSE);
        fake.put("secret_port_close", "CLOSED");
        IllegalArgumentException secretPort = assertThrows(IllegalArgumentException.class,
                () -> S0SecretRotateWithoutRestartHonesty.verify(fake));
        assertTrue(secretPort.getMessage().contains("secret_port_close"));

        fake.put("secret_port_close", S0SecretRotateWithoutRestartHonesty.SECRET_PORT_CLOSE);
        fake.put("openbao_introduced", "true");
        IllegalArgumentException openbao = assertThrows(IllegalArgumentException.class,
                () -> S0SecretRotateWithoutRestartHonesty.verify(fake));
        assertTrue(openbao.getMessage().contains("openbao_introduced"));

        fake.put("openbao_introduced", "false");
        fake.put("kms_introduced", "true");
        IllegalArgumentException kms = assertThrows(IllegalArgumentException.class,
                () -> S0SecretRotateWithoutRestartHonesty.verify(fake));
        assertTrue(kms.getMessage().contains("kms_introduced"));
    }

    @Test
    void honestyGateRejectsRedoOf143And180() {
        Map<String, String> fake = new HashMap<>(
                S0SecretRotateWithoutRestartHonesty.parseReport(fixture("honest-skip.env")));
        fake.put("redo_143", "true");
        IllegalArgumentException redo143 = assertThrows(IllegalArgumentException.class,
                () -> S0SecretRotateWithoutRestartHonesty.verify(fake));
        assertTrue(redo143.getMessage().contains("redo_143"));

        fake.put("redo_143", "false");
        fake.put("redo_180", "true");
        IllegalArgumentException redo180 = assertThrows(IllegalArgumentException.class,
                () -> S0SecretRotateWithoutRestartHonesty.verify(fake));
        assertTrue(redo180.getMessage().contains("redo_180"));
    }

    @Test
    void leftoverDocsKeepAllSiblingRowsAndRefuseS0Close() throws IOException {
        String leftover = readExisting(repoFile("docs/s0-secret-rotate-without-restart-honesty.md"));
        String lower = leftover.toLowerCase(Locale.ROOT);
        assertTrue(leftover.contains("#143"));
        assertTrue(leftover.contains("#180"));
        assertTrue(leftover.contains("System.getenv"));
        assertTrue(leftover.contains("Keep ALL sibling leftover rows")
                || leftover.contains("Keep **ALL** sibling leftover rows"));
        assertTrue(leftover.contains("s0-secret-rotate-without-restart"));
        assertTrue(leftover.contains("s0-secret-getenv-snapshot-honesty"));
        assertTrue(leftover.contains("does **not** redo #143") || leftover.contains("Does **not** redo #143")
                || leftover.contains("不重做 #143"));
        assertTrue(leftover.contains("does **not** redo #180") || leftover.contains("Does **not** redo #180")
                || leftover.contains("不重做 #180"));
        assertTrue(leftover.contains("OpenBao"));
        assertFalse(leftover.toLowerCase(Locale.ROOT).contains("product s0 is closed"));
        assertFalse(leftover.contains("产品 S0 已关闭"));
        assertFalse(leftover.contains("SecretPort 已关闭"));
        assertFalse(lower.contains("product secretport closed"));

        String readme = readExisting(repoFile("docs/README.md"));
        assertTrue(readme.contains("[s0-secret-rotate-without-restart](./s0-secret-rotate-without-restart.md)"));
        assertTrue(readme.contains("[s0-secret-getenv-snapshot-honesty](./s0-secret-getenv-snapshot-honesty.md)"));
        assertTrue(readme.contains(
                "[s0-secret-rotate-without-restart-honesty](./s0-secret-rotate-without-restart-honesty.md)"));
        assertTrue(readme.contains("[g4-p1-staging-audit-events](./g4-p1-staging-audit-events.md)"));
        assertTrue(readme.contains("[g4-p1-staging-audit-events-honesty](./g4-p1-staging-audit-events-honesty.md)"));
        assertTrue(readme.contains("[g6-recovery-port-dry-run-honesty](./g6-recovery-port-dry-run-honesty.md)"));
        assertTrue(readme.contains("[s0s1-live-redis-minio-isolation-honesty](./s0s1-live-redis-minio-isolation-honesty.md)"));
        assertTrue(readme.contains("[g4-hot-purge-90d-honesty](./g4-hot-purge-90d-honesty.md)"));
        assertTrue(readme.contains("[g17-separation-honesty](./g17-separation-honesty.md)"));
        assertTrue(readme.contains("[g4-export-wizard](./g4-export-wizard.md)"));
        assertTrue(readme.contains("[g4-export-wizard-deny](./g4-export-wizard-deny.md)"));
        assertTrue(readme.contains("[g4-pkce-spa-verifier](./g4-pkce-spa-verifier.md)"));
        assertTrue(readme.contains("[g4-pkce-spa-verifier-honesty](./g4-pkce-spa-verifier-honesty.md)"));
        assertTrue(readme.contains("[g10-identity-drift-honesty](./g10-identity-drift-honesty.md)"));
        assertTrue(readme.contains("[g15-native-bridge-honesty](./g15-native-bridge-honesty.md)"));
        assertTrue(readme.contains("[g5-browser-failed-publish](./g5-browser-failed-publish.md)"));
        assertTrue(readme.contains("[g1-fieldpolicy-mask-second-auth-honesty](./g1-fieldpolicy-mask-second-auth-honesty.md)"));
        assertTrue(readme.contains("[g7-withdraw-browser-honesty](./g7-withdraw-browser-honesty.md)"));
        assertTrue(readme.contains("[g2-form-write-honesty](./g2-form-write-honesty.md)"));
        assertTrue(readme.contains("[g9-redis-down-invalidate-honesty](./g9-redis-down-invalidate-honesty.md)"));
        assertTrue(readme.contains("[g17-mfa-recovery-email-honesty](./g17-mfa-recovery-email-honesty.md)"));
        assertTrue(readme.contains("[linux-arm-profile-honesty](./linux-arm-profile-honesty.md)"));
        assertTrue(readme.contains("[linux-arm-profile-leftover-honesty](./linux-arm-profile-leftover-honesty.md)"));
        assertTrue(readme.contains("[g7-query-expense-travel](./g7-query-expense-travel.md)"));
        assertTrue(readme.contains("[g7-query-expense-travel-honesty](./g7-query-expense-travel-honesty.md)"));
        assertFalse(readme.toLowerCase(Locale.ROOT).contains("product s0 closed"));
        assertFalse(readme.contains("产品 S0 已关闭"));
        assertFalse(readme.contains("SecretPort 已关闭"));
    }

    @Test
    void claimListKeepsBothS0LeftoverRows() throws IOException {
        String text = readExisting(repoFile("docs/planning/团队领取任务清单_V1.1.md"));
        assertTrue(text.contains("s0-secret-rotate-without-restart.md"));
        assertTrue(text.contains("s0-secret-getenv-snapshot-honesty.md"));
        assertTrue(text.contains("s0-secret-rotate-without-restart-honesty.md"));
        assertTrue(text.contains("#143"));
        assertTrue(text.contains("#180"));
        assertTrue(text.contains("不宣称完整 S0") || text.contains("不宣称产品 S0"));
        assertTrue(text.contains("OpenBao"));
        assertTrue(text.contains("不要重做 #143") || text.contains("不重做 #143"));
        assertFalse(text.contains("产品 S0 已关闭"));
        assertFalse(text.contains("完整 S0 已关闭"));
        assertFalse(text.contains("SecretPort 已关闭"));
    }

    @Test
    void productSystemAdapterStillUsesGetenvSnapshot() throws IOException {
        Path file = Path.of("src/main/java/one/nexus/crypto/EnvSecretAdapter.java");
        if (!Files.isRegularFile(file)) {
            file = Path.of("nexus-crypto-core/src/main/java/one/nexus/crypto/EnvSecretAdapter.java");
        }
        assertTrue(Files.isRegularFile(file), file.toAbsolutePath().toString());
        String text = Files.readString(file);
        assertTrue(text.contains("System::getenv"));
        assertTrue(text.contains("process-start snapshot") || text.contains("unmodifiable"));
        assertFalse(text.contains("OpenBaoAdapter"));
        assertFalse(text.contains("CloudKms"));
    }

    private static Path fixture(String name) {
        Path first = Path.of("scripts/s0-secret-rotate-without-restart-honesty/fixtures/" + name);
        if (first.toFile().isFile()) {
            return first;
        }
        return Path.of("../scripts/s0-secret-rotate-without-restart-honesty/fixtures/" + name);
    }

    private static Path repoFile(String relative) {
        Path nested = Path.of("..", relative);
        if (Files.isRegularFile(nested)) {
            return nested;
        }
        return Path.of(relative);
    }

    private static String readExisting(Path file) throws IOException {
        assertTrue(Files.isRegularFile(file), file.toAbsolutePath().toString());
        return Files.readString(file);
    }
}
