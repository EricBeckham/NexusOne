package one.nexus.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.EnvSecretAdapter;
import one.nexus.crypto.JcaCryptoPort;
import one.nexus.crypto.LocalEncryptedSecretAdapter;
import one.nexus.crypto.LocalThenEnvSecretPort;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.crypto.SecretNames;
import one.nexus.crypto.SecretPort;
import one.nexus.file.infra.CountingObjectStorage;
import one.nexus.file.infra.MinioObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class FileSecretPortWiringTest {
    @Test
    void s3StorageReadsCredentialsFromSecretPort() {
        FileProperties properties = new FileProperties();
        properties.setStorage("s3");
        properties.getS3().setEndpoint("http://127.0.0.1:9100");

        Map<String, String> env = new HashMap<>();
        env.put("S3_ACCESS_KEY", "from-properties-must-not-win");
        env.put("S3_SECRET_KEY", "from-env-secret");
        SecretPort secrets = new LocalThenEnvSecretPort(
                new LocalEncryptedSecretAdapter(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL)),
                new EnvSecretAdapter(CryptoSuite.INTERNATIONAL, SecretNames.envBindings(), env::get));
        secrets.put(SecretNames.S3_ACCESS_KEY, "from-port".getBytes(StandardCharsets.UTF_8));

        assertEquals("from-port", secrets.getUtf8(SecretNames.S3_ACCESS_KEY));
        assertEquals("from-env-secret", secrets.getUtf8(SecretNames.S3_SECRET_KEY));

        ObjectStoragePort storage = new FileConfiguration().objectStoragePort(properties, available(secrets));
        assertInstanceOf(MinioObjectStorage.class, storage);
    }

    @Test
    void s3StorageRequiresSecretPort() {
        FileProperties properties = new FileProperties();
        properties.setStorage("s3");
        properties.getS3().setEndpoint("http://127.0.0.1:9100");
        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> new FileConfiguration().objectStoragePort(properties, missing()));
        assertTrue(thrown.getMessage().contains("SecretPort"));
        assertTrue(thrown.getMessage().contains("OpenBao"));
    }

    @Test
    void fromSecretPortDoesNotReadOrdinaryProperties() {
        SecretPort secrets = new LocalEncryptedSecretAdapter(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        NexusException thrown = assertThrows(
                NexusException.class,
                () -> MinioObjectStorage.fromSecretPort("http://127.0.0.1:9100", secrets));
        assertEquals(ErrorCode.SECRET_NOT_FOUND, thrown.errorCode());
    }

    @Test
    void fromSecretPortSnapshotsCredentialsAtConstruct() throws IOException {
        Path file = Path.of("src/main/java/one/nexus/file/infra/MinioObjectStorage.java");
        if (!Files.isRegularFile(file)) {
            file = Path.of("nexus-file-core/src/main/java/one/nexus/file/infra/MinioObjectStorage.java");
        }
        assertTrue(Files.isRegularFile(file), file.toAbsolutePath().toString());
        String text = Files.readString(file);
        String lower = text.toLowerCase(Locale.ROOT);
        assertTrue(text.contains("secrets.getUtf8(SecretNames.S3_ACCESS_KEY)"));
        assertTrue(text.contains("secrets.getUtf8(SecretNames.S3_SECRET_KEY)"));
        assertFalse(text.contains("private final SecretPort"));
        assertTrue(lower.contains("snapshotted at construct") || lower.contains("snapshot"));
        assertFalse(lower.contains("openbao"));
        assertFalse(lower.contains("kms"));
    }

    @Test
    void memoryStorageDoesNotNeedSecretPort() {
        FileProperties properties = new FileProperties();
        properties.setStorage("memory");
        ObjectStoragePort storage = new FileConfiguration().objectStoragePort(properties, missing());
        assertInstanceOf(CountingObjectStorage.class, storage);
    }

    private static ObjectProvider<SecretPort> available(SecretPort port) {
        return new ObjectProvider<>() {
            @Override
            public SecretPort getObject() {
                return port;
            }

            @Override
            public SecretPort getObject(Object... args) {
                return port;
            }

            @Override
            public SecretPort getIfAvailable() {
                return port;
            }
        };
    }

    private static ObjectProvider<SecretPort> missing() {
        return new ObjectProvider<>() {
            @Override
            public SecretPort getObject() {
                throw new IllegalStateException("no SecretPort");
            }

            @Override
            public SecretPort getObject(Object... args) {
                throw new IllegalStateException("no SecretPort");
            }

            @Override
            public SecretPort getIfAvailable() {
                return null;
            }
        };
    }
}
