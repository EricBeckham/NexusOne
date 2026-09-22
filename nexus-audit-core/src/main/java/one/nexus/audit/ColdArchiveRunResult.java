package one.nexus.audit;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Outcome of one cold-archive copy. {@link #written()} is new object puts;
 * {@link #skipped()} is idempotent exists. Does not delete hot rows.
 */
public record ColdArchiveRunResult(
        int written,
        int skipped,
        int considered,
        Instant cutoff,
        List<String> writtenKeys) {

    public ColdArchiveRunResult {
        if (written < 0) {
            throw new IllegalArgumentException("written must be >= 0");
        }
        if (skipped < 0) {
            throw new IllegalArgumentException("skipped must be >= 0");
        }
        if (considered < 0) {
            throw new IllegalArgumentException("considered must be >= 0");
        }
        Objects.requireNonNull(cutoff, "cutoff");
        writtenKeys = List.copyOf(writtenKeys == null ? List.of() : writtenKeys);
    }
}
