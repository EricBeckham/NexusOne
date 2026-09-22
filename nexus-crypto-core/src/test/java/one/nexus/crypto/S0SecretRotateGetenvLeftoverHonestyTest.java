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
import one.nexus.crypto.S0SecretRotateGetenvLeftoverHonesty.LiveRotateRestart;
import one.nexus.crypto.S0SecretRotateGetenvLeftoverHonesty.LocalThenEnvHarness;
import one.nexus.crypto.S0SecretRotateGetenvLeftoverHonesty.ParentHarness;
import org.junit.jupiter.api.Test;

/**
 * S0 leftover remainder beyond #223 / #180 / #200: parent leftover harness
 * stays always-green; LocalThenEnv in-process rotate / overlay PASS; live OS
 * getenv / JVM restart / LocalThenEnv.system() / JDBC / S3 client walks are
 * honest SKIP. Does not redo #143 / #180 / #200 / #223. Does not claim
 * product S0 / SecretPort close.
 */
class S0SecretRotateGetenvLeftoverHonestyTest {

    @Test
    void parentHarnessStillPassesWithoutRedoing223Or180Or200() {
        ParentHarness parent = S0SecretRotateGetenvLeftoverHonesty.proveParentHarness();
        assertEquals("PASS", parent.leftover180());
        assertEquals("PASS", parent.leftover200());
        assertEquals("PASS", parent.leftover223());
        assertEquals("PASS", parent.leftover180Cells());
        assertEquals("PASS", parent.leftover200Cells());
        assertEquals("PASS", parent.leftover223Cells());
    }

    @Test
    void localThenEnvInProcessRotateAndOverlayStayPass() {
        LocalThenEnvHarness lte = S0SecretRotateGetenvLeftoverHonesty.proveLocalThenEnv();
        assertEquals("PASS", lte.mapRotate());
        assertEquals("PASS", lte.overlayWins());
    }

    @Test
    void liveRotateRestartAndLocalThenEnvSystemAreHonestSkipOnJava21Snapshot() {
        LiveRotateRestart live = S0SecretRotateGetenvLeftoverHonesty.probeLiveRotateRestart();
        assertEquals("SKIP", live.liveOsGetenvRotate());
        assertEquals("SKIP", live.liveRestart());
        assertEquals("SKIP", live.liveLocalThenEnvSystemGetenv());
        assertEquals("SKIP", live.liveJdbcRuntimeSecretPort());
        assertEquals("SKIP", live.liveS3ClientRotate());
        assertFalse(live.osSkipReason().isBlank());
        assertFalse(live.restartSkipReason().isBlank());
        assertFalse(live.lteSkipReason().isBlank());
        assertFalse(live.jdbcSkipReason().isBlank());
        assertFalse(live.s3SkipReason().isBlank());
        assertFalse(live.osRotateObserved());
        assertFalse(live.liveRestartObserved());
        assertFalse(live.lteSystemObserved());
        assertFalse(live.jdbcObserved());
        assertFalse(live.s3Observed());
        assertTrue(live.getenvUnmodifiable() || live.getenvMutable());
        assertTrue(live.osSkipReason().contains("snapshot")
                || live.osSkipReason().contains("unmodifiable")
                || live.osSkipReason().contains("not observed"));
        assertTrue(live.restartSkipReason().contains("restart"));
        assertTrue(live.lteSkipReason().contains("LocalThenEnv")
                || live.lteSkipReason().contains("system"));
        assertTrue(live.osSkipReason().contains("#223") || live.lteSkipReason().contains("#223"));
    }

    @Test
    void honestyGateAcceptsHonestSkipTemplate() {
        ParentHarness parent = S0SecretRotateGetenvLeftoverHonesty.proveParentHarness();
        LocalThenEnvHarness lte = S0SecretRotateGetenvLeftoverHonesty.proveLocalThenEnv();
        LiveRotateRestart live = S0SecretRotateGetenvLeftoverHonesty.probeLiveRotateRestart();
        Map<String, String> report = S0SecretRotateGetenvLeftoverHonesty.honestSkipTemplate(parent, lte, live);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        S0SecretRotateGetenvLeftoverHonesty.verify(report);
        assertEquals("PASS", report.get("leftover_223_harness"));
        assertEquals("PASS", report.get("leftover_180_harness"));
        assertEquals("PASS", report.get("leftover_200_harness"));
        assertEquals("PASS", report.get("env_map_rotate"));
        assertEquals("PASS", report.get("local_then_env_map_rotate"));
        assertEquals("PASS", report.get("local_then_env_overlay_wins"));
        assertEquals("SKIP", report.get("live_os_getenv_rotate"));
        assertEquals("SKIP", report.get("live_restart"));
        assertEquals("SKIP", report.get("live_local_then_env_system_getenv"));
        assertEquals("SKIP", report.get("live_jdbc_runtime_secretport"));
        assertEquals("SKIP", report.get("live_s3_client_rotate"));
        assertEquals(S0SecretRotateGetenvLeftoverHonesty.S0_CLOSE, report.get("s0_close"));
        assertEquals(S0SecretRotateGetenvLeftoverHonesty.SECRET_PORT_CLOSE, report.get("secret_port_close"));
        assertEquals("false", report.get("redo_223"));
        assertEquals("false", report.get("openbao_introduced"));
        assertEquals("closed_unchanged", report.get("g18_status"));
        assertEquals("false", report.get("audit_port_changed"));
        assertEquals("false", report.get("p0_m0_redone"));
        assertTrue(report.get("leftover_rows").contains("s0-secret-rotate-getenv-remainder-honesty"));
        assertTrue(report.get("leftover_rows").contains("g7-withdraw-browser-evidence-honesty"));
        assertTrue(report.get("leftover_rows").contains("g4-hot-purge-staging-leftover-honesty"));
        assertTrue(report.get("leftover_rows").contains("g2-h5-every-page-form-write-remainder-honesty"));
        assertTrue(report.get("leftover_rows").contains("g9-redis-down-invalidate-remainder-honesty"));
        assertTrue(report.get("leftover_rows").contains("g7-query-expense-travel-honesty-remainder"));
        assertTrue(report.get("leftover_rows").contains("g1-fieldpolicy-mask-second-auth-remainder-honesty"));
        assertTrue(report.get("leftover_rows").contains("g17-mfa-recovery-vue-otp-remainder-honesty"));
        assertTrue(report.get("leftover_rows").contains("g5-publishhook-ddl-remainder-honesty"));
        S0SecretRotateGetenvLeftoverHonesty.verify(
                S0SecretRotateGetenvLeftoverHonesty.parseReport(fixture("honest-skip.env")));
    }

    @Test
    void honestyGateRejectsFakeOsGetenvPass() {
        Map<String, String> fake = new HashMap<>(
                S0SecretRotateGetenvLeftoverHonesty.parseReport(fixture("fake-os-getenv-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> S0SecretRotateGetenvLeftoverHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("live_os_getenv_rotate"));
    }

    @Test
    void honestyGateRejectsFakeLiveRestartPass() {
        Map<String, String> fake = new HashMap<>(
                S0SecretRotateGetenvLeftoverHonesty.parseReport(fixture("fake-live-restart-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> S0SecretRotateGetenvLeftoverHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("live_restart"));
    }

    @Test
    void honestyGateRejectsFakeLocalThenEnvSystemPass() {
        Map<String, String> fake = new HashMap<>(
                S0SecretRotateGetenvLeftoverHonesty.parseReport(fixture("fake-local-then-env-system-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> S0SecretRotateGetenvLeftoverHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("live_local_then_env_system_getenv"));
    }

    @Test
    void honestyGateRejectsS0AndSecretPortCloseAndRedo() {
        IllegalArgumentException close = assertThrows(IllegalArgumentException.class,
                () -> S0SecretRotateGetenvLeftoverHonesty.verify(
                        S0SecretRotateGetenvLeftoverHonesty.parseReport(fixture("fake-s0-close.env"))));
        assertTrue(close.getMessage().contains("s0_close") || close.getMessage().contains("secret_port_close"));

        IllegalArgumentException redo = assertThrows(IllegalArgumentException.class,
                () -> S0SecretRotateGetenvLeftoverHonesty.verify(
                        S0SecretRotateGetenvLeftoverHonesty.parseReport(fixture("fake-redone.env"))));
        assertTrue(redo.getMessage().contains("redo_223")
                || redo.getMessage().contains("redo_180")
                || redo.getMessage().contains("redo_200"));
    }

    @Test
    void leftoverDocsKeepAllSiblingRowsAndRefuseS0Close() throws IOException {
        String leftover = readExisting(repoFile("docs/s0-secret-rotate-getenv-leftover-honesty.md"));
        String lower = leftover.toLowerCase(Locale.ROOT);
        assertTrue(leftover.contains("#223"));
        assertTrue(leftover.contains("#180"));
        assertTrue(leftover.contains("#200"));
        assertTrue(leftover.contains("LocalThenEnv") || leftover.contains("LocalThenEnvSecretPort"));
        assertTrue(leftover.contains("System.getenv") || leftover.contains("getenv"));
        assertTrue(leftover.contains("Keep ALL sibling leftover rows")
                || leftover.contains("Keep **ALL** sibling leftover rows"));
        assertTrue(leftover.contains("s0-secret-rotate-getenv-remainder-honesty"));
        assertTrue(leftover.contains("s0-secret-rotate-without-restart-honesty"));
        assertTrue(leftover.contains("s0-secret-getenv-snapshot-honesty"));
        assertTrue(leftover.contains("g7-withdraw-browser-evidence-honesty"));
        assertTrue(leftover.contains("g4-hot-purge-staging-leftover-honesty"));
        assertTrue(leftover.contains("g9-live-multi-jvm-replica-remainder-honesty"));
        assertTrue(leftover.contains("g2-h5-every-page-form-write-remainder-honesty"));
        assertTrue(leftover.contains("g9-redis-down-invalidate-remainder-honesty"));
        assertTrue(leftover.contains("g7-query-expense-travel-honesty-remainder"));
        assertTrue(leftover.contains("g1-fieldpolicy-mask-second-auth-remainder-honesty"));
        assertTrue(leftover.contains("g17-mfa-recovery-vue-otp-remainder-honesty"));
        assertTrue(leftover.contains("g5-publishhook-ddl-remainder-honesty"));
        assertTrue(leftover.contains("does **not** redo #223") || leftover.contains("Does **not** redo #223")
                || leftover.contains("不重做 #223"));
        assertTrue(leftover.contains("OpenBao"));
        assertFalse(leftover.contains("产品 S0 已关闭"));
        assertFalse(leftover.contains("SecretPort 已关闭"));
        assertFalse(lower.contains("product secretport closed"));
        assertFalse(lower.contains("product s0 is closed"));

        String readme = readExisting(repoFile("docs/README.md"));
        for (String row : S0SecretRotateGetenvLeftoverHonesty.README_ROWS) {
            assertTrue(readme.contains("[" + row + "](./" + row + ".md)"), "missing leftover row " + row);
        }
        assertFalse(readme.toLowerCase(Locale.ROOT).contains("product s0 closed"));
        assertFalse(readme.contains("产品 S0 已关闭"));
        assertFalse(readme.contains("SecretPort 已关闭"));
    }

    @Test
    void claimListKeepsS0LeftoverRows() throws IOException {
        String text = readExisting(repoFile("docs/planning/团队领取任务清单_V1.1.md"));
        assertTrue(text.contains("s0-secret-rotate-without-restart.md"));
        assertTrue(text.contains("s0-secret-getenv-snapshot-honesty.md"));
        assertTrue(text.contains("s0-secret-rotate-without-restart-honesty.md"));
        assertTrue(text.contains("s0-secret-rotate-getenv-remainder-honesty.md"));
        assertTrue(text.contains("s0-secret-rotate-getenv-leftover-honesty.md"));
        assertTrue(text.contains("#143"));
        assertTrue(text.contains("#180"));
        assertTrue(text.contains("#200"));
        assertTrue(text.contains("#223"));
        assertTrue(text.contains("不宣称完整 S0") || text.contains("不宣称产品 S0"));
        assertTrue(text.contains("OpenBao"));
        assertTrue(text.contains("不要重做 #143") || text.contains("不重做 #143"));
        assertTrue(text.contains("不要重做 #223") || text.contains("不重做 #223")
                || text.contains("不要重做 #143/#180/#200/#223"));
        assertTrue(text.contains("不要重开 G18"));
        assertFalse(text.contains("产品 S0 已关闭"));
        assertFalse(text.contains("完整 S0 已关闭"));
        assertFalse(text.contains("SecretPort 已关闭"));
    }

    @Test
    void productSystemAdapterStillUsesGetenvSnapshotAndLocalThenEnv() throws IOException {
        Path envFile = Path.of("src/main/java/one/nexus/crypto/EnvSecretAdapter.java");
        if (!Files.isRegularFile(envFile)) {
            envFile = Path.of("nexus-crypto-core/src/main/java/one/nexus/crypto/EnvSecretAdapter.java");
        }
        assertTrue(Files.isRegularFile(envFile), envFile.toAbsolutePath().toString());
        String envText = Files.readString(envFile);
        assertTrue(envText.contains("System::getenv"));
        assertTrue(envText.contains("process-start snapshot") || envText.contains("unmodifiable"));
        assertFalse(envText.contains("OpenBaoAdapter"));
        assertFalse(envText.contains("CloudKms"));

        Path lteFile = Path.of("src/main/java/one/nexus/crypto/LocalThenEnvSecretPort.java");
        if (!Files.isRegularFile(lteFile)) {
            lteFile = Path.of("nexus-crypto-core/src/main/java/one/nexus/crypto/LocalThenEnvSecretPort.java");
        }
        assertTrue(Files.isRegularFile(lteFile), lteFile.toAbsolutePath().toString());
        String lteText = Files.readString(lteFile);
        assertTrue(lteText.contains("EnvSecretAdapter.system"));
        assertTrue(lteText.contains("System::getenv"));
        assertTrue(lteText.contains("local+env"));
        assertFalse(lteText.contains("OpenBaoAdapter"));
        assertFalse(lteText.contains("CloudKms"));
    }

    private static Path fixture(String name) {
        Path first = Path.of("scripts/s0-secret-rotate-getenv-leftover-honesty/fixtures/" + name);
        if (first.toFile().isFile()) {
            return first;
        }
        return Path.of("../scripts/s0-secret-rotate-getenv-leftover-honesty/fixtures/" + name);
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
