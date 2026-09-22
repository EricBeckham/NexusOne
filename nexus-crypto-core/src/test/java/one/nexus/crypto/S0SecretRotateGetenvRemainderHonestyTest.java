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
import one.nexus.crypto.S0SecretRotateGetenvRemainderHonesty.LiveRotateRestart;
import one.nexus.crypto.S0SecretRotateGetenvRemainderHonesty.ParentHarness;
import org.junit.jupiter.api.Test;

/**
 * S0 leftover remainder beyond #180 / #200: parent leftover harness stays
 * always-green; live OS getenv rotate / live JVM restart observation is honest
 * SKIP. Does not redo #143 / #180 / #200. Does not claim product S0 /
 * SecretPort close.
 */
class S0SecretRotateGetenvRemainderHonestyTest {

    @Test
    void parentHarnessStillPassesWithoutRedoing180Or200() {
        ParentHarness parent = S0SecretRotateGetenvRemainderHonesty.proveParentHarness();
        assertEquals("PASS", parent.leftover180());
        assertEquals("PASS", parent.leftover200());
        assertEquals("PASS", parent.leftover180Cells());
        assertEquals("PASS", parent.leftover200Cells());
    }

    @Test
    void liveRotateRestartIsHonestSkipOnJava21Snapshot() {
        LiveRotateRestart live = S0SecretRotateGetenvRemainderHonesty.probeLiveRotateRestart();
        assertEquals("SKIP", live.liveOsGetenvRotate());
        assertEquals("SKIP", live.liveRestart());
        assertFalse(live.osSkipReason().isBlank());
        assertFalse(live.restartSkipReason().isBlank());
        assertFalse(live.osRotateObserved());
        assertFalse(live.liveRestartObserved());
        assertTrue(live.getenvUnmodifiable() || live.getenvMutable());
        assertTrue(live.osSkipReason().contains("snapshot")
                || live.osSkipReason().contains("unmodifiable")
                || live.osSkipReason().contains("not observed"));
        assertTrue(live.restartSkipReason().contains("restart"));
        assertTrue(live.osSkipReason().contains("#180") || live.osSkipReason().contains("#200"));
    }

    @Test
    void honestyGateAcceptsHonestSkipTemplate() {
        ParentHarness parent = S0SecretRotateGetenvRemainderHonesty.proveParentHarness();
        LiveRotateRestart live = S0SecretRotateGetenvRemainderHonesty.probeLiveRotateRestart();
        Map<String, String> report = S0SecretRotateGetenvRemainderHonesty.honestSkipTemplate(parent, live);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        S0SecretRotateGetenvRemainderHonesty.verify(report);
        assertEquals("PASS", report.get("leftover_180_harness"));
        assertEquals("PASS", report.get("leftover_200_harness"));
        assertEquals("PASS", report.get("env_map_rotate"));
        assertEquals("SKIP", report.get("live_os_getenv_rotate"));
        assertEquals("SKIP", report.get("live_restart"));
        assertEquals(S0SecretRotateGetenvRemainderHonesty.S0_CLOSE, report.get("s0_close"));
        assertEquals(S0SecretRotateGetenvRemainderHonesty.SECRET_PORT_CLOSE, report.get("secret_port_close"));
        assertEquals("false", report.get("redo_180"));
        assertEquals("false", report.get("redo_200"));
        assertEquals("false", report.get("openbao_introduced"));
        assertEquals("closed_unchanged", report.get("g18_status"));
        assertEquals("false", report.get("audit_port_changed"));
        assertEquals("false", report.get("p0_m0_redone"));
        assertTrue(report.get("leftover_rows").contains("s0-secret-getenv-snapshot-honesty"));
        assertTrue(report.get("leftover_rows").contains("s0-secret-rotate-without-restart-honesty"));
        assertTrue(report.get("leftover_rows").contains("g9-sentinel-cluster-remainder-honesty"));
        S0SecretRotateGetenvRemainderHonesty.verify(
                S0SecretRotateGetenvRemainderHonesty.parseReport(fixture("honest-skip.env")));
    }

    @Test
    void honestyGateRejectsFakeOsGetenvPass() {
        Map<String, String> fake = new HashMap<>(
                S0SecretRotateGetenvRemainderHonesty.parseReport(fixture("fake-os-getenv-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> S0SecretRotateGetenvRemainderHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("live_os_getenv_rotate"));
    }

    @Test
    void honestyGateRejectsFakeLiveRestartPass() {
        Map<String, String> fake = new HashMap<>(
                S0SecretRotateGetenvRemainderHonesty.parseReport(fixture("fake-live-restart-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> S0SecretRotateGetenvRemainderHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("live_restart"));
    }

    @Test
    void honestyGateRejectsS0AndSecretPortCloseAndRedo() {
        IllegalArgumentException close = assertThrows(IllegalArgumentException.class,
                () -> S0SecretRotateGetenvRemainderHonesty.verify(
                        S0SecretRotateGetenvRemainderHonesty.parseReport(fixture("fake-s0-close.env"))));
        assertTrue(close.getMessage().contains("s0_close") || close.getMessage().contains("secret_port_close"));

        IllegalArgumentException redo = assertThrows(IllegalArgumentException.class,
                () -> S0SecretRotateGetenvRemainderHonesty.verify(
                        S0SecretRotateGetenvRemainderHonesty.parseReport(fixture("fake-redone.env"))));
        assertTrue(redo.getMessage().contains("redo_180") || redo.getMessage().contains("redo_200"));
    }

    @Test
    void leftoverDocsKeepAllSiblingRowsAndRefuseS0Close() throws IOException {
        String leftover = readExisting(repoFile("docs/s0-secret-rotate-getenv-remainder-honesty.md"));
        String lower = leftover.toLowerCase(Locale.ROOT);
        assertTrue(leftover.contains("#180"));
        assertTrue(leftover.contains("#200"));
        assertTrue(leftover.contains("System.getenv") || leftover.contains("getenv"));
        assertTrue(leftover.contains("Keep ALL sibling leftover rows")
                || leftover.contains("Keep **ALL** sibling leftover rows"));
        assertTrue(leftover.contains("s0-secret-rotate-without-restart-honesty"));
        assertTrue(leftover.contains("s0-secret-getenv-snapshot-honesty"));
        assertTrue(leftover.contains("g9-sentinel-cluster-remainder-honesty"));
        assertTrue(leftover.contains("g5-publishhook-ddl-honesty"));
        assertTrue(leftover.contains("g17-gm-tls-nginx-handshake-honesty"));
        assertTrue(leftover.contains("g1-queryast-export-honesty"));
        assertTrue(leftover.contains("g15-wx-dt-native-bridge-remainder"));
        assertTrue(leftover.contains("g17-separation-leftover-honesty"));
        assertTrue(leftover.contains("g4-pkce-spa-verifier-remainder-honesty"));
        assertTrue(leftover.contains("does **not** redo #180") || leftover.contains("Does **not** redo #180")
                || leftover.contains("不重做 #180"));
        assertTrue(leftover.contains("does **not** redo #200") || leftover.contains("Does **not** redo #200")
                || leftover.contains("不重做 #200"));
        assertTrue(leftover.contains("OpenBao"));
        assertFalse(leftover.contains("产品 S0 已关闭"));
        assertFalse(leftover.contains("SecretPort 已关闭"));
        assertFalse(lower.contains("product secretport closed"));
        assertFalse(lower.contains("product s0 is closed"));

        String readme = readExisting(repoFile("docs/README.md"));
        for (String row : S0SecretRotateGetenvRemainderHonesty.README_ROWS) {
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
        assertTrue(text.contains("#143"));
        assertTrue(text.contains("#180"));
        assertTrue(text.contains("#200"));
        assertTrue(text.contains("不宣称完整 S0") || text.contains("不宣称产品 S0"));
        assertTrue(text.contains("OpenBao"));
        assertTrue(text.contains("不要重做 #143") || text.contains("不重做 #143"));
        assertTrue(text.contains("不要重做 #180") || text.contains("不重做 #180")
                || text.contains("不要重做 #143/#180"));
        assertTrue(text.contains("不要重开 G18"));
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
        Path first = Path.of("scripts/s0-secret-rotate-getenv-remainder-honesty/fixtures/" + name);
        if (first.toFile().isFile()) {
            return first;
        }
        return Path.of("../scripts/s0-secret-rotate-getenv-remainder-honesty/fixtures/" + name);
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
