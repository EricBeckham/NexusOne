package one.nexus.crypto.tls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bouncycastle.tls.CipherSuite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DualStackTlsProbeTest {
    @Test
    void simultaneousIntlAndGmHandshakesNegotiateDistinctStacks() {
        DualStackTlsMaterial material = DualStackTlsMaterial.ephemeral();
        DualStackTlsProbe.Result result = DualStackTlsProbe.localhostSimultaneous(material);
        assertTrue(result.succeeded(), String.valueOf(result));
        assertTrue(result.simultaneous());
        assertTrue(result.crossTalkRejected());
        assertTrue(result.dualServerCertsDistinct());
        assertTrue(result.international().protocol().contains("1.3"), result.international().protocol());
        assertTrue(result.gm().protocol().contains("1.3"), result.gm().protocol());
        assertEquals(CipherSuite.TLS_AES_128_GCM_SHA256, result.international().cipherSuiteId());
        assertEquals("TLS_AES_128_GCM_SHA256", result.international().cipherSuite());
        assertEquals(CipherSuite.TLS_SM4_GCM_SM3, result.gm().cipherSuiteId());
        assertEquals(GmTlsOids.TLS_SM4_GCM_SM3_NAME, result.gm().cipherSuite());
        assertEquals(DualStackTlsProbe.INTL_SERVER_PAYLOAD, result.international().payload());
        assertEquals(DualStackTlsProbe.GM_SERVER_PAYLOAD, result.gm().payload());
        assertTrue(result.international().serverSubject().contains("intl-server"));
        assertTrue(result.gm().serverSubject().contains("gm-server-sign"));
        System.out.println("G17 dual-stack " + result);
    }

    @Test
    void dualStackStoresWritePemKeysWithoutEmbeddingPassword(@TempDir Path dir) throws Exception {
        DualStackTlsMaterial material = DualStackTlsMaterial.ephemeral();
        char[] password = "nexus-g17-dual-stack-test".toCharArray();
        material.write(dir, password);
        assertTrue(Files.exists(dir.resolve("public/intl-server.pem")));
        assertTrue(Files.exists(dir.resolve("public/server-sign.pem")));
        assertTrue(Files.exists(dir.resolve("public/server-encrypt.pem")));
        assertTrue(Files.exists(dir.resolve("keys/intl-server.key")));
        assertTrue(Files.exists(dir.resolve("keys/gm-server-sign.key")));
        assertTrue(Files.exists(dir.resolve("keys/gm-server-encrypt.key")));
        String hints = Files.readString(dir.resolve("dual-stack.hints.yaml"), StandardCharsets.UTF_8);
        assertTrue(hints.contains("intl-server.p12"));
        assertTrue(hints.contains("server-encrypt.p12"));
        assertFalse(hints.contains("nexus-g17-dual-stack-test"));
        assertFalse(hints.contains("等保") && hints.contains("closed"));
        String fingerprints = Files.readString(dir.resolve("public/dual-stack-fingerprints.txt"), StandardCharsets.UTF_8);
        assertTrue(fingerprints.contains(DualStackTlsMaterial.sha256Fingerprint(material.intlServer().certificate())));
        assertTrue(fingerprints.contains(GmTlsDualMaterial.sm3Fingerprint(material.gm().serverSign().certificate())));
    }
}
