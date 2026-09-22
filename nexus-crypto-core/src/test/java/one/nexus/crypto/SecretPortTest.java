package one.nexus.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;

class SecretPortTest {
    @Test
    void localAdapterRoundTripAndKeyVersion() {
        CryptoPort crypto = JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL);
        SecretPort secrets = new LocalEncryptedSecretAdapter(crypto);
        byte[] value = "db-password-not-for-git".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        SecretRef ref = secrets.put("datasource.password", value);
        assertEquals(1, ref.secretVersion());
        assertEquals(crypto.currentKey(), ref.cryptoKey());
        assertArrayEquals(value, secrets.get("datasource.password"));
        secrets.put("datasource.password", "rotated".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(2, secrets.find("datasource.password").orElseThrow().secretVersion());
        secrets.delete("datasource.password");
        NexusException thrown = assertThrows(NexusException.class, () -> secrets.get("datasource.password"));
        assertEquals(ErrorCode.SECRET_NOT_FOUND, thrown.errorCode());
        assertTrue(secrets.find("missing").isEmpty());
    }

    @Test
    void gmSuiteEncryptsSecretEnvelope() {
        CryptoPort crypto = JcaCryptoPort.ephemeral(CryptoSuite.GM);
        SecretPort secrets = new LocalEncryptedSecretAdapter(crypto);
        secrets.put("connector.token", new byte[] {1, 2, 3, 4});
        assertEquals(CryptoSuite.GM, secrets.find("connector.token").orElseThrow().cryptoKey().suite());
        assertArrayEquals(new byte[] {1, 2, 3, 4}, secrets.get("connector.token"));
    }
}
