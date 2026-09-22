package one.nexus.crypto.tls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bouncycastle.tls.CipherSuite;
import org.junit.jupiter.api.Test;

class GmTlsHandshakeTest {
    @Test
    void localhostMtlsHandshakeNegotiatesTls13Sm4GcmSm3() {
        GmTlsDualMaterial material = GmTlsDualMaterial.ephemeral();
        GmTlsHandshakeProbe.Result result = GmTlsHandshakeProbe.localhostMtls(material);
        assertTrue(result.succeeded());
        assertTrue(result.mtls());
        assertTrue(result.dualServerCertsDistinct());
        assertTrue(result.sm3withSm2());
        assertTrue(result.protocol().contains("1.3"), result.protocol());
        assertEquals(CipherSuite.TLS_SM4_GCM_SM3, result.cipherSuiteId());
        assertEquals(GmTlsOids.TLS_SM4_GCM_SM3_NAME, result.cipherSuite());
        assertEquals(GmTlsHandshakeProbe.SERVER_PAYLOAD, result.payload());
        assertTrue(result.serverCertificateCount() >= 2);
        assertTrue(result.clientCertificateCount() >= 1);
        assertTrue(result.serverSignSubject().contains("gm-server-sign"));
        assertTrue(result.serverEncryptSubject().contains("gm-server-encrypt"));
        System.out.println("G17 GM TLS handshake " + result);
    }

    @Test
    void localhostServerOnlyHandshakeStillUsesSignCertAndSm4() {
        GmTlsHandshakeProbe.Result result = GmTlsHandshakeProbe.localhostServerOnly(GmTlsDualMaterial.ephemeral());
        assertTrue(result.succeeded());
        assertEquals(CipherSuite.TLS_SM4_GCM_SM3, result.cipherSuiteId());
        assertEquals(GmTlsHandshakeProbe.SERVER_PAYLOAD, result.payload());
    }

    @Test
    void mtlsWithoutClientCertIsRejected() {
        GmTlsDualMaterial material = GmTlsDualMaterial.ephemeral();
        assertThrows(RuntimeException.class, () -> GmTlsHandshakeProbe.localhostMtlsWithoutClientCert(material));
    }
}
