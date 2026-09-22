package one.nexus.audit;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import one.nexus.common.audit.AuditEvent;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.crypto.CryptoPort;

/**
 * Per-event hash chain (batch size 1). A later batch-seal can hash a range of
 * {@code eventHash} values. G4/G18 emitters write through {@link AuditPort};
 * this class only links already-captured events.
 */
public final class AuditHashChain {
    public static final byte[] GENESIS = new byte[32];

    private final CryptoPort crypto;

    public AuditHashChain(CryptoPort crypto) {
        this.crypto = Objects.requireNonNull(crypto, "crypto");
    }

    public byte[] hash(long chainIndex, byte[] previousHash, AuditEvent event) {
        byte[] prev = previousHash == null || previousHash.length == 0 ? GENESIS : previousHash;
        byte[] payload = CanonicalAudit.encode(chainIndex, prev, event);
        byte[] digest = crypto.digest(payload);
        if (digest.length != 32) {
            throw new IllegalStateException("digest must be 32 bytes, got " + digest.length);
        }
        return digest;
    }

    public ChainedAuditEvent link(long chainIndex, byte[] previousHash, AuditEvent event) {
        byte[] prev = previousHash == null || previousHash.length == 0 ? GENESIS.clone() : previousHash.clone();
        byte[] eventHash = hash(chainIndex, prev, event);
        return new ChainedAuditEvent(
                chainIndex,
                event,
                prev,
                eventHash,
                crypto.suite(),
                ChainedAuditEvent.keyVersionOf(crypto.currentKey()));
    }

    /**
     * Verifies remaining hot rows in order. A full chain still starts at
     * index 0 / {@link #GENESIS}. After a 90-day hot prefix purge the first
     * remaining row may start at a later index; the stored {@code previousHash}
     * of that suffix head is accepted so the live tail can still be checked.
     * Genesis-to-head proof for purged rows lives in archive / cold copies.
     */
    public HashChainVerification verify(List<ChainedAuditEvent> inOrder) {
        if (inOrder == null || inOrder.isEmpty()) {
            return HashChainVerification.ok(0);
        }
        long expectedIndex = inOrder.get(0).chainIndex();
        if (expectedIndex < 0) {
            return HashChainVerification.broken(0, "unexpected chain index " + expectedIndex);
        }
        byte[] prev = expectedIndex == 0 ? GENESIS : inOrder.get(0).previousHash();
        long checked = 0;
        for (ChainedAuditEvent linked : inOrder) {
            if (linked.chainIndex() != expectedIndex) {
                return HashChainVerification.broken(checked, "unexpected chain index " + linked.chainIndex());
            }
            if (!Arrays.equals(prev, linked.previousHash())) {
                return HashChainVerification.broken(checked, "previous hash mismatch at " + linked.chainIndex());
            }
            if (linked.suite() != crypto.suite()) {
                return HashChainVerification.broken(checked, "suite mismatch at " + linked.chainIndex());
            }
            byte[] expected = hash(linked.chainIndex(), linked.previousHash(), linked.event());
            if (!linked.hashesEqual(expected)) {
                return HashChainVerification.broken(checked, "event hash mismatch at " + linked.chainIndex());
            }
            prev = linked.eventHash();
            expectedIndex++;
            checked++;
        }
        return HashChainVerification.ok(checked);
    }

    public void requireIntact(List<ChainedAuditEvent> inOrder) {
        HashChainVerification verification = verify(inOrder);
        if (!verification.intact()) {
            throw new NexusException(ErrorCode.AUDIT_CHAIN_BROKEN, 500, verification.failure());
        }
    }
}
