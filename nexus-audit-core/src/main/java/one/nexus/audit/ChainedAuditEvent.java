package one.nexus.audit;

import java.util.Arrays;
import java.util.Objects;
import one.nexus.common.audit.AuditEvent;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.KeyRef;

/**
 * Append-only store record: capture + chain links. Application code must not
 * update or delete these rows.
 */
public record ChainedAuditEvent(
        long chainIndex,
        AuditEvent event,
        byte[] previousHash,
        byte[] eventHash,
        CryptoSuite suite,
        String digestKeyVersion) {

    public ChainedAuditEvent {
        if (chainIndex < 0) {
            throw new IllegalArgumentException("chainIndex must be >= 0");
        }
        Objects.requireNonNull(event, "event");
        previousHash = previousHash == null ? new byte[0] : previousHash.clone();
        eventHash = eventHash == null ? new byte[0] : eventHash.clone();
        Objects.requireNonNull(suite, "suite");
        if (digestKeyVersion == null || digestKeyVersion.isBlank()) {
            throw new IllegalArgumentException("digestKeyVersion is required");
        }
    }

    public byte[] previousHash() {
        return previousHash.clone();
    }

    public byte[] eventHash() {
        return eventHash.clone();
    }

    public static String keyVersionOf(KeyRef ref) {
        return ref.keyId() + ":v" + ref.version();
    }

    public boolean hashesEqual(byte[] expectedEventHash) {
        return Arrays.equals(eventHash, expectedEventHash);
    }
}
