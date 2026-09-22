package one.nexus.crypto;

import java.util.Arrays;
import java.util.Objects;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;

/**
 * AEAD ciphertext envelope: suite + key version + 12-byte nonce + ciphertext||tag.
 *
 * <p>Wire format {@code NXC1}: 4 magic, 1 suite, 2 keyId length, keyId UTF-8,
 * 4 version, 12 nonce, remaining payload.
 */
public record SealedBytes(KeyRef key, byte[] nonce, byte[] ciphertextAndTag) {
    public static final byte[] MAGIC = {'N', 'X', 'C', '1'};
    public static final int NONCE_LENGTH = 12;
    public static final int TAG_BITS = 128;

    public SealedBytes {
        Objects.requireNonNull(key, "key");
        nonce = copyRequired(nonce, "nonce");
        ciphertextAndTag = copyRequired(ciphertextAndTag, "ciphertextAndTag");
        if (nonce.length != NONCE_LENGTH) {
            throw new IllegalArgumentException("GCM nonce must be 12 bytes");
        }
        if (ciphertextAndTag.length < TAG_BITS / 8) {
            throw new IllegalArgumentException("ciphertext too short for GCM tag");
        }
    }

    public byte[] encode() {
        byte[] keyId = key.keyId().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (keyId.length > 65535) {
            throw new IllegalArgumentException("keyId too long");
        }
        byte[] out = new byte[4 + 1 + 2 + keyId.length + 4 + nonce.length + ciphertextAndTag.length];
        System.arraycopy(MAGIC, 0, out, 0, 4);
        out[4] = (byte) (key.suite() == CryptoSuite.INTERNATIONAL ? 1 : 2);
        out[5] = (byte) ((keyId.length >>> 8) & 0xFF);
        out[6] = (byte) (keyId.length & 0xFF);
        System.arraycopy(keyId, 0, out, 7, keyId.length);
        int offset = 7 + keyId.length;
        int version = key.version();
        out[offset] = (byte) ((version >>> 24) & 0xFF);
        out[offset + 1] = (byte) ((version >>> 16) & 0xFF);
        out[offset + 2] = (byte) ((version >>> 8) & 0xFF);
        out[offset + 3] = (byte) (version & 0xFF);
        offset += 4;
        System.arraycopy(nonce, 0, out, offset, nonce.length);
        offset += nonce.length;
        System.arraycopy(ciphertextAndTag, 0, out, offset, ciphertextAndTag.length);
        return out;
    }

    public static SealedBytes decode(byte[] wire) {
        if (wire == null || wire.length < 4 + 1 + 2 + 4 + NONCE_LENGTH + TAG_BITS / 8) {
            throw integrity("sealed blob is truncated");
        }
        if (wire[0] != MAGIC[0] || wire[1] != MAGIC[1] || wire[2] != MAGIC[2] || wire[3] != MAGIC[3]) {
            throw integrity("sealed blob magic is not NXC1");
        }
        CryptoSuite suite = switch (wire[4]) {
            case 1 -> CryptoSuite.INTERNATIONAL;
            case 2 -> CryptoSuite.GM;
            default -> throw integrity("unknown cipher suite id");
        };
        int keyIdLength = ((wire[5] & 0xFF) << 8) | (wire[6] & 0xFF);
        int offset = 7;
        if (wire.length < offset + keyIdLength + 4 + NONCE_LENGTH + TAG_BITS / 8) {
            throw integrity("sealed blob is truncated");
        }
        String keyId = new String(wire, offset, keyIdLength, java.nio.charset.StandardCharsets.UTF_8);
        offset += keyIdLength;
        int version = ((wire[offset] & 0xFF) << 24)
                | ((wire[offset + 1] & 0xFF) << 16)
                | ((wire[offset + 2] & 0xFF) << 8)
                | (wire[offset + 3] & 0xFF);
        offset += 4;
        byte[] nonce = Arrays.copyOfRange(wire, offset, offset + NONCE_LENGTH);
        offset += NONCE_LENGTH;
        byte[] payload = Arrays.copyOfRange(wire, offset, wire.length);
        return new SealedBytes(new KeyRef(suite, keyId, version), nonce, payload);
    }

    public byte[] nonce() {
        return nonce.clone();
    }

    public byte[] ciphertextAndTag() {
        return ciphertextAndTag.clone();
    }

    private static byte[] copyRequired(byte[] value, String name) {
        if (value == null || value.length == 0) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.clone();
    }

    private static NexusException integrity(String message) {
        return new NexusException(ErrorCode.CRYPTO_INTEGRITY, 400, message);
    }
}
