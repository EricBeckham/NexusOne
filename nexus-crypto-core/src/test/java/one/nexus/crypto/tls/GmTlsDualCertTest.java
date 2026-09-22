package one.nexus.crypto.tls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GmTlsDualCertTest {
    @Test
    void dualSm2CertsHaveDistinctUsageAndPublishedOids() throws Exception {
        GmTlsDualMaterial material = GmTlsDualMaterial.ephemeral();
        X509Certificate sign = material.serverSign().certificate();
        X509Certificate encrypt = material.serverEncrypt().certificate();
        assertTrue(material.dualServerCertsDistinct());
        assertNotEquals(sign.getSerialNumber(), encrypt.getSerialNumber());
        assertEquals(GmTlsOids.SM3_WITH_SM2, sign.getSigAlgOID());
        assertEquals(GmTlsOids.SM3_WITH_SM2, encrypt.getSigAlgOID());
        assertEquals(GmTlsOids.SM3_WITH_SM2, material.client().certificate().getSigAlgOID());
        assertTrue(sign.getKeyUsage()[0], "sign cert digitalSignature");
        assertTrue(encrypt.getKeyUsage()[2] || encrypt.getKeyUsage()[4], "encrypt cert keyEncipherment/keyAgreement");
        assertFalse(sign.getSerialNumber().equals(material.client().certificate().getSerialNumber()));
        assertEquals(64, GmTlsDualMaterial.sm3Fingerprint(sign).length());
        assertNotEquals(GmTlsDualMaterial.sm3Fingerprint(sign), GmTlsDualMaterial.sm3Fingerprint(encrypt));
        System.out.println("G17 dual-cert vectors " + GmTlsVersions.summary());
        System.out.println("sign-oid=" + sign.getSigAlgOID() + " sm4-gcm=0x"
                + Integer.toHexString(GmTlsOids.TLS_SM4_GCM_SM3));
    }

    @Test
    void dualCertStoresLoadWithoutEmbeddingPasswordInHints(@TempDir Path dir) throws Exception {
        GmTlsDualMaterial original = GmTlsDualMaterial.ephemeral();
        char[] password = "nexus-g17-local-test".toCharArray();
        original.write(dir, password);
        GmTlsDualMaterial loaded = GmTlsDualMaterial.load(dir, password);
        assertEquals(
                original.serverSign().certificate().getSerialNumber(),
                loaded.serverSign().certificate().getSerialNumber());
        assertEquals(
                original.serverEncrypt().certificate().getSerialNumber(),
                loaded.serverEncrypt().certificate().getSerialNumber());
        assertTrue(Files.exists(dir.resolve("public/server-sign.pem")));
        assertTrue(Files.exists(dir.resolve("public/server-encrypt.pem")));
        assertFalse(Files.exists(dir.resolve("server-sign.key")));
        String hints = Files.readString(dir.resolve("connector.hints.yaml"), StandardCharsets.UTF_8);
        assertTrue(hints.contains("server-sign.p12"));
        assertTrue(hints.contains("server-encrypt.p12"));
        assertFalse(hints.contains("nexus-g17-local-test"));
        String fingerprints = Files.readString(dir.resolve("public/fingerprints.txt"), StandardCharsets.UTF_8);
        assertTrue(fingerprints.contains(GmTlsDualMaterial.sm3Fingerprint(original.serverSign().certificate())));
    }
}
