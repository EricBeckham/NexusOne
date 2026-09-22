package one.nexus.audit;

import java.time.Instant;
import java.util.Objects;

/**
 * Outcome of one archive Job run or port call. Copy sets {@link #copied()};
 * 365-day expiry sets {@link #purged()} and {@link #purgeImplemented()} true.
 */
public record ArchiveRunResult(
        int copied,
        int purged,
        int archiveSize,
        Instant cutoff,
        boolean purgeImplemented) {

    public ArchiveRunResult {
        if (copied < 0) {
            throw new IllegalArgumentException("copied must be >= 0");
        }
        if (purged < 0) {
            throw new IllegalArgumentException("purged must be >= 0");
        }
        if (archiveSize < 0) {
            throw new IllegalArgumentException("archiveSize must be >= 0");
        }
        Objects.requireNonNull(cutoff, "cutoff");
    }

    public static ArchiveRunResult copied(int copied, int archiveSize, Instant cutoff) {
        return new ArchiveRunResult(copied, 0, archiveSize, cutoff, false);
    }

    public static ArchiveRunResult purged(int purged, int archiveSize, Instant cutoff) {
        return new ArchiveRunResult(0, purged, archiveSize, cutoff, true);
    }

    public ArchiveRunResult withPurge(ArchiveRunResult purge) {
        Objects.requireNonNull(purge, "purge");
        return new ArchiveRunResult(
                copied, purge.purged(), purge.archiveSize(), cutoff, purge.purgeImplemented());
    }
}
