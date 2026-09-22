package one.nexus.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;

class CryptoPortContractTest {
    private static final byte[] MESSAGE = "NexusOne P0-10 crypto contract".getBytes(StandardCharsets.UTF_8);

    @ParameterizedTest
    @EnumSource(CryptoSuite.class)
    void p0_10RoundTripAndTamperReject(CryptoSuite suite) {
        CryptoPort port = JcaCryptoPort.ephemeral(suite);
        byte[] changed = MESSAGE.clone();
        changed[0] ^= 1;
        assertFalse(Arrays.equals(port.digest(MESSAGE), port.digest(changed)), suite + " digest tamper");
        assertFalse(Arrays.equals(port.mac(MESSAGE), port.mac(changed)), suite + " MAC tamper");
        SealedBytes sealed = port.encrypt(MESSAGE);
        assertEquals(suite, sealed.key().suite());
        assertEquals(1, sealed.key().version());
        assertArrayEquals(MESSAGE, port.decrypt(sealed));
        assertArrayEquals(MESSAGE, port.decrypt(sealed.encode()));
        byte[] tampered = sealed.encode();
        tampered[tampered.length - 1] ^= 1;
        NexusException thrown = assertThrows(NexusException.class, () -> port.decrypt(tampered));
        assertEquals(ErrorCode.CRYPTO_INTEGRITY, thrown.errorCode());
        byte[] proof = port.sign(MESSAGE);
        assertTrue(port.verify(MESSAGE, proof));
        assertFalse(port.verify(changed, proof));
    }

    @Test
    void suitesCannotDecryptEachOther() {
        CryptoPort intl = JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL);
        CryptoPort gm = JcaCryptoPort.ephemeral(CryptoSuite.GM);
        byte[] wire = intl.encrypt(MESSAGE).encode();
        NexusException thrown = assertThrows(NexusException.class, () -> gm.decrypt(wire));
        assertEquals(ErrorCode.CRYPTO_SUITE_MISMATCH, thrown.errorCode());
    }

    @Test
    void keyRotationDecryptsPreviousVersion() {
        CryptoPort port = JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL);
        SealedBytes v1 = port.encrypt(MESSAGE);
        KeyRef previous = v1.key();
        KeyRef rotated = port.rotate();
        assertNotEquals(previous.version(), rotated.version());
        SealedBytes v2 = port.encrypt(MESSAGE);
        assertEquals(2, v2.key().version());
        assertArrayEquals(MESSAGE, port.decrypt(v1));
        assertArrayEquals(MESSAGE, port.decrypt(v2));
    }

    @Test
    void envelopeCarriesSuiteAndKeyVersion() {
        CryptoPort port = JcaCryptoPort.fromMasterKey(
                CryptoSuite.GM, "deploy-a", 3, Hex.decode("00112233445566778899aabbccddeeff"));
        SealedBytes sealed = port.encrypt(MESSAGE);
        SealedBytes decoded = SealedBytes.decode(sealed.encode());
        assertEquals(CryptoSuite.GM, decoded.key().suite());
        assertEquals("deploy-a", decoded.key().keyId());
        assertEquals(3, decoded.key().version());
    }
}
