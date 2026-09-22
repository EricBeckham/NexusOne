package one.nexus.audit;

import java.time.Instant;
import java.util.Objects;

/**
 * Outcome of one archive Job run. {@link #purgeImplemented()} is always false in
 * this drop: 365-day expiry is a documented target, not a delete path.
 */
public record ArchiveRunResult(
        int copied,
        int archiveSize,
        Instant cutoff,
        boolean purgeImplemented) {

    public ArchiveRunResult {
        if (copied < 0) {
            throw new IllegalArgumentException("copied must be >= 0");
        }
        if (archiveSize < 0) {
            throw new IllegalArgumentException("archiveSize must be >= 0");
        }
        Objects.requireNonNull(cutoff, "cutoff");
    }

    public static ArchiveRunResult copied(int copied, int archiveSize, Instant cutoff) {
        return new ArchiveRunResult(copied, archiveSize, cutoff, false);
    }
}
