package one.nexus.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * S0 leftover: env-backed rotate-without-restart. Mutable-map env is re-read
 * on the next {@code get}. OS {@code System.getenv()} is a Java 21 process-start
 * snapshot and is honestly SKIP. Does not introduce OpenBao / KMS.
 */
class SecretPortRotateWithoutRestartTest {
    @Test
    void envMapRotateIsPickedUpOnNextGet() {
        Map<String, String> env = new HashMap<>();
        env.put("S3_ACCESS_KEY", "before-rotate");
        SecretPort secrets = new EnvSecretAdapter(CryptoSuite.INTERNATIONAL, SecretNames.envBindings(), env::get);

        assertEquals("before-rotate", secrets.getUtf8(SecretNames.S3_ACCESS_KEY));
        env.put("S3_ACCESS_KEY", "after-rotate");
        assertEquals("after-rotate", secrets.getUtf8(SecretNames.S3_ACCESS_KEY));
        assertEquals("after-rotate", secrets.getUtf8("S3_ACCESS_KEY"));
        assertTrue(secrets.find(SecretNames.S3_ACCESS_KEY).isPresent());
    }

    @Test
    void envMapRotateToBlankBecomesNotFound() {
        Map<String, String> env = new HashMap<>();
        env.put("S3_SECRET_KEY", "present");
        SecretPort secrets = new EnvSecretAdapter(CryptoSuite.INTERNATIONAL, SecretNames.envBindings(), env::get);
        assertEquals("present", secrets.getUtf8(SecretNames.S3_SECRET_KEY));

        env.put("S3_SECRET_KEY", "   ");
        assertTrue(secrets.find(SecretNames.S3_SECRET_KEY).isEmpty());
        NexusException thrown = assertThrows(NexusException.class, () -> secrets.get(SecretNames.S3_SECRET_KEY));
        assertEquals(ErrorCode.SECRET_NOT_FOUND, thrown.errorCode());
    }

    @Test
    void localThenEnvPicksUpEnvRotateWhenNoOverlay() {
        Map<String, String> env = new HashMap<>();
        env.put("S3_ACCESS_KEY", "env-before");
        SecretPort secrets = new LocalThenEnvSecretPort(
                new LocalEncryptedSecretAdapter(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL)),
                new EnvSecretAdapter(CryptoSuite.INTERNATIONAL, SecretNames.envBindings(), env::get));

        assertEquals("local+env", secrets.backend());
        assertEquals("env-before", secrets.getUtf8(SecretNames.S3_ACCESS_KEY));
        env.put("S3_ACCESS_KEY", "env-after");
        assertEquals("env-after", secrets.getUtf8(SecretNames.S3_ACCESS_KEY));
    }

    @Test
    void localOverlayStillWinsAfterEnvRotate() {
        Map<String, String> env = new HashMap<>();
        env.put("S3_SECRET_KEY", "env-before");
        SecretPort secrets = new LocalThenEnvSecretPort(
                new LocalEncryptedSecretAdapter(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL)),
                new EnvSecretAdapter(CryptoSuite.INTERNATIONAL, SecretNames.envBindings(), env::get));

        secrets.put(SecretNames.S3_SECRET_KEY, "from-overlay".getBytes(StandardCharsets.UTF_8));
        env.put("S3_SECRET_KEY", "env-after");
        assertEquals("from-overlay", secrets.getUtf8(SecretNames.S3_SECRET_KEY));
        secrets.delete(SecretNames.S3_SECRET_KEY);
        assertEquals("env-after", secrets.getUtf8(SecretNames.S3_SECRET_KEY));
    }

    @Test
    void processGetenvRotateWithoutRestartIsJvmSnapshot() {
        Map<String, String> processEnv = System.getenv();
        String probe = "NEXUS_S0_ROTATE_PROBE";
        try {
            processEnv.put(probe, "after-rotate");
        } catch (UnsupportedOperationException expected) {
            Assumptions.abort(
                    "SKIP: Java 21 System.getenv() is an unmodifiable process-start snapshot; "
                            + "cannot rotate the OS environ of a running JVM. "
                            + "EnvSecretAdapter.system() uses System::getenv, so an OS env rotate "
                            + "is not observed without process restart. "
                            + "Mutable-map env rotation is picked up (envMapRotateIsPickedUpOnNextGet). "
                            + "Not OpenBao/KMS. Not a product S0 close.");
        }
        SecretPort system = EnvSecretAdapter.system(CryptoSuite.INTERNATIONAL);
        assertEquals("after-rotate", system.getUtf8(probe));
        Assumptions.abort(
                "SKIP: this JVM allowed System.getenv() mutation; leftover does not claim a "
                        + "supported in-process OS env rotate API for the product default.");
    }

    @Test
    void leftoverDocsStayHonest() throws IOException {
        Path leftover = leftoverDoc();
        assertTrue(Files.isRegularFile(leftover), leftover.toAbsolutePath().toString());
        String text = Files.readString(leftover);
        String lower = text.toLowerCase(Locale.ROOT);
        assertTrue(text.contains("rotate") || lower.contains("rotate"));
        assertTrue(lower.contains("system.getenv") || text.contains("System.getenv"));
        assertTrue(lower.contains("skip"));
        assertFalse(lower.contains("openbao adapter"));
        assertFalse(lower.contains("cloud kms adapter"));
        assertFalse(text.contains("产品 S0 已关闭"));
        assertFalse(lower.contains("product s0 is closed"));
        assertFalse(lower.contains("product s0 closed"));
        assertTrue(text.contains("does **not** claim a product S0 close")
                || text.contains("not a product S0 close")
                || text.contains("不宣称完整 S0 关闭")
                || text.contains("不宣称产品 S0 关闭"));
        assertTrue(text.contains("G18") && (text.contains("closed") || text.contains("关闭")));
    }

    @Test
    void envAdapterDoesNotCacheAndDoesNotCallOpenBao() throws IOException {
        Path file = Path.of("src/main/java/one/nexus/crypto/EnvSecretAdapter.java");
        if (!Files.isRegularFile(file)) {
            file = Path.of("nexus-crypto-core/src/main/java/one/nexus/crypto/EnvSecretAdapter.java");
        }
        assertTrue(Files.isRegularFile(file), file.toAbsolutePath().toString());
        String text = Files.readString(file);
        String lower = text.toLowerCase(Locale.ROOT);
        assertTrue(text.contains("env.apply(envName)"));
        assertFalse(text.contains("ConcurrentHashMap"));
        assertFalse(text.contains("OpenBaoAdapter"));
        assertFalse(text.contains("CloudKms"));
        assertTrue(text.contains("does not call OpenBao") || text.contains("It does not call OpenBao"));
        assertTrue(text.contains("System::getenv"));
        assertTrue(lower.contains("process-start snapshot") || lower.contains("unmodifiable"));
    }

    private static Path leftoverDoc() {
        Path leftover = Path.of("../docs/s0-secret-rotate-without-restart.md");
        if (Files.isRegularFile(leftover)) {
            return leftover;
        }
        return Path.of("docs/s0-secret-rotate-without-restart.md");
    }
}
