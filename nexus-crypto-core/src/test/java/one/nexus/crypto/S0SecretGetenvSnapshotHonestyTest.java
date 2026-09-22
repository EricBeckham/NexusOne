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
import one.nexus.crypto.S0SecretGetenvSnapshotHonesty.EnvMapRotate;
import one.nexus.crypto.S0SecretGetenvSnapshotHonesty.OsGetenvInventory;
import org.junit.jupiter.api.Test;

/**
 * S0 leftover honesty beyond #143: mutable env-map rotate stays always-green;
 * OS {@code System.getenv()} startup snapshot is honest SKIP. Does not redo
 * the #143 suite. Does not introduce OpenBao / KMS. Does not claim product S0.
 */
class S0SecretGetenvSnapshotHonestyTest {

    @Test
    void envMapRotateIsPickedUpWithoutRedoing143Suite() {
        EnvMapRotate rotate = S0SecretGetenvSnapshotHonesty.probeEnvMapRotate();
        assertTrue(rotate.pickedUp());
        assertEquals("PASS", rotate.verdict());
    }

    @Test
    void osGetenvProbeIsHonestSkipOnJava21Snapshot() {
        OsGetenvInventory os = S0SecretGetenvSnapshotHonesty.probeOsGetenv();
        assertEquals("SKIP", os.osGetenvRotate());
        assertFalse(os.skipReason().isBlank());
        assertTrue(os.getenvUnmodifiable() || os.getenvMutable());
        assertFalse(os.osRotateObserved());
        assertTrue(os.skipReason().contains("snapshot")
                || os.skipReason().contains("unmodifiable")
                || os.skipReason().contains("not observed"));
        assertTrue(os.skipReason().contains("System.getenv")
                || os.skipReason().contains("OS env"));
    }

    @Test
    void honestyGateAcceptsHonestSkipTemplate() {
        EnvMapRotate envMap = S0SecretGetenvSnapshotHonesty.probeEnvMapRotate();
        OsGetenvInventory os = S0SecretGetenvSnapshotHonesty.probeOsGetenv();
        Map<String, String> report = S0SecretGetenvSnapshotHonesty.honestSkipTemplate(envMap, os);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        S0SecretGetenvSnapshotHonesty.verify(report);
        assertEquals("PASS", report.get("env_map_rotate"));
        assertEquals("SKIP", report.get("os_getenv_rotate"));
        assertEquals(S0SecretGetenvSnapshotHonesty.S0_CLOSE, report.get("s0_close"));
        assertEquals("false", report.get("s0_143_redone"));
        assertEquals("false", report.get("openbao_introduced"));
        assertEquals("false", report.get("kms_introduced"));
        assertEquals("false", report.get("p0_m0_redone"));
        assertEquals("closed_unchanged", report.get("g18_status"));
        S0SecretGetenvSnapshotHonesty.verify(
                S0SecretGetenvSnapshotHonesty.parseReport(fixture("honest-skip.env")));
    }

    @Test
    void honestyGateRejectsFakeOsGetenvPass() {
        Map<String, String> fake = new HashMap<>(
                S0SecretGetenvSnapshotHonesty.parseReport(fixture("fake-os-getenv-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> S0SecretGetenvSnapshotHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("os_getenv"));
    }

    @Test
    void honestyGateRejectsS0CloseAndOpenBao() {
        Map<String, String> fake = new HashMap<>(
                S0SecretGetenvSnapshotHonesty.parseReport(fixture("honest-skip.env")));
        fake.put("s0_close", "CLOSED");
        IllegalArgumentException close = assertThrows(IllegalArgumentException.class,
                () -> S0SecretGetenvSnapshotHonesty.verify(fake));
        assertTrue(close.getMessage().contains("s0_close"));

        fake.put("s0_close", S0SecretGetenvSnapshotHonesty.S0_CLOSE);
        fake.put("openbao_introduced", "true");
        IllegalArgumentException openbao = assertThrows(IllegalArgumentException.class,
                () -> S0SecretGetenvSnapshotHonesty.verify(fake));
        assertTrue(openbao.getMessage().contains("openbao_introduced"));

        fake.put("openbao_introduced", "false");
        fake.put("kms_introduced", "true");
        IllegalArgumentException kms = assertThrows(IllegalArgumentException.class,
                () -> S0SecretGetenvSnapshotHonesty.verify(fake));
        assertTrue(kms.getMessage().contains("kms_introduced"));

        fake.put("kms_introduced", "false");
        fake.put("s0_143_redone", "true");
        IllegalArgumentException redo = assertThrows(IllegalArgumentException.class,
                () -> S0SecretGetenvSnapshotHonesty.verify(fake));
        assertTrue(redo.getMessage().contains("s0_143_redone"));
    }

    @Test
    void leftoverDocsKeepBothS0RowsAndRefuseS0Close() throws IOException {
        String readme = readExisting(repoFile("docs/README.md"));
        assertTrue(readme.contains("[s0-secret-rotate-without-restart](./s0-secret-rotate-without-restart.md)"));
        assertTrue(readme.contains("[s0-secret-getenv-snapshot-honesty](./s0-secret-getenv-snapshot-honesty.md)"));
        assertTrue(readme.contains("g7-withdraw-browser-honesty"));
        assertTrue(readme.contains("g9-redis-down-invalidate-honesty"));
        assertTrue(readme.contains("g17-mfa-recovery-email-honesty"));
        assertTrue(readme.contains("g2-form-write-honesty"));
        assertFalse(readme.toLowerCase(Locale.ROOT).contains("product s0 closed"));
        assertFalse(readme.contains("产品 S0 已关闭"));

        String leftover = readExisting(repoFile("docs/s0-secret-getenv-snapshot-honesty.md"));
        assertTrue(leftover.contains("Keep both leftover rows") || leftover.contains("Keep **both** leftover rows"));
        assertTrue(leftover.contains("#143"));
        assertTrue(leftover.contains("System.getenv"));
        assertTrue(leftover.contains("does **not** claim a product S0 close")
                || leftover.contains("Does **not** claim a product S0 close")
                || leftover.contains("不宣称完整 S0 关闭"));
        assertTrue(leftover.contains("does **not** redo #143") || leftover.contains("Does **not** redo #143")
                || leftover.contains("不重做 #143"));
        assertTrue(leftover.contains("OpenBao"));
        assertFalse(leftover.toLowerCase(Locale.ROOT).contains("product s0 is closed"));
        assertFalse(leftover.contains("产品 S0 已关闭"));
    }

    @Test
    void claimListS0RowStaysHonest() throws IOException {
        String text = readExisting(repoFile("docs/planning/团队领取任务清单_V1.1.md"));
        assertTrue(text.contains("s0-secret-rotate-without-restart.md"));
        assertTrue(text.contains("s0-secret-getenv-snapshot-honesty.md"));
        assertTrue(text.contains("#143"));
        assertTrue(text.contains("不宣称完整 S0") || text.contains("不宣称产品 S0"));
        assertTrue(text.contains("OpenBao"));
        assertFalse(text.contains("产品 S0 已关闭"));
        assertFalse(text.contains("完整 S0 已关闭"));
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
        Path first = Path.of("scripts/s0-secret-getenv-snapshot-honesty/fixtures/" + name);
        if (first.toFile().isFile()) {
            return first;
        }
        return Path.of("../scripts/s0-secret-getenv-snapshot-honesty/fixtures/" + name);
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
