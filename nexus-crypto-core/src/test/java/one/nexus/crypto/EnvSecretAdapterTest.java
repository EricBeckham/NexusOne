package one.nexus.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import org.junit.jupiter.api.Test;

class EnvSecretAdapterTest {
    @Test
    void readsLogicalNameAndRawEnvAlias() {
        Map<String, String> env = new HashMap<>();
        env.put("S3_SECRET_KEY", "minio-secret");
        env.put("S3_ACCESS_KEY", "minio-user");
        SecretPort secrets = new EnvSecretAdapter(CryptoSuite.INTERNATIONAL, SecretNames.envBindings(), env::get);

        assertEquals("env", secrets.backend());
        assertArrayEquals("minio-secret".getBytes(StandardCharsets.UTF_8), secrets.get(SecretNames.S3_SECRET_KEY));
        assertEquals("minio-user", secrets.getUtf8("S3_ACCESS_KEY"));
        assertEquals("env", secrets.find(SecretNames.S3_SECRET_KEY).orElseThrow().cryptoKey().keyId());
        assertTrue(secrets.find("missing").isEmpty());
    }

    @Test
    void blankEnvIsNotFound() {
        Map<String, String> env = Map.of("NEXUS_DB_PASSWORD", "");
        SecretPort secrets = new EnvSecretAdapter(CryptoSuite.INTERNATIONAL, SecretNames.envBindings(), env::get);
        NexusException thrown = assertThrows(NexusException.class, () -> secrets.get(SecretNames.DATASOURCE_PASSWORD));
        assertEquals(ErrorCode.SECRET_NOT_FOUND, thrown.errorCode());
    }

    @Test
    void putAndDeleteAreReadOnly() {
        SecretPort secrets = new EnvSecretAdapter(CryptoSuite.GM, SecretNames.envBindings(), name -> null);
        NexusException put = assertThrows(
                NexusException.class, () -> secrets.put(SecretNames.S3_SECRET_KEY, new byte[] {1}));
        assertEquals(ErrorCode.SECRET_BACKEND_READ_ONLY, put.errorCode());
        NexusException delete = assertThrows(NexusException.class, () -> secrets.delete(SecretNames.S3_SECRET_KEY));
        assertEquals(ErrorCode.SECRET_BACKEND_READ_ONLY, delete.errorCode());
        assertEquals(CryptoSuite.GM, secrets.suite());
    }
}
