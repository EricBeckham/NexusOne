package one.nexus.audit;

import java.time.Instant;
import java.util.Objects;

/**
 * Outcome of one 90-day hot-prefix purge. {@link #purged()} is DELETE count on
 * {@code platform.audit_record}. {@link #skippedUncopied()} is aged hot rows
 * that stayed because they had no safe copy or were blocked by a younger prefix.
 */
public record HotPurgeRunResult(int purged, int remainingHot, int skippedUncopied, Instant cutoff) {
    public HotPurgeRunResult {
        if (purged < 0) {
            throw new IllegalArgumentException("purged must be >= 0");
        }
        if (remainingHot < 0) {
            throw new IllegalArgumentException("remainingHot must be >= 0");
        }
        if (skippedUncopied < 0) {
            throw new IllegalArgumentException("skippedUncopied must be >= 0");
        }
        Objects.requireNonNull(cutoff, "cutoff");
    }
}
