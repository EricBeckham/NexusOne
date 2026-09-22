package one.nexus.audit;

import java.time.Instant;

/**
 * Dry-run result of {@link AuditHotRetentionJob}. {@code archiveDeferred} is
 * always {@code true} until a 365-day archive store exists.
 */
public record AuditRetentionScan(
        Instant scannedAt,
        Instant hotCutoff,
        long hotCount,
        long pastHotCount,
        long totalCount,
        boolean archiveDeferred,
        String note) {

    public AuditRetentionScan {
        if (hotCount < 0 || pastHotCount < 0 || totalCount < 0) {
            throw new IllegalArgumentException("counts must be >= 0");
        }
        if (hotCount + pastHotCount != totalCount) {
            throw new IllegalArgumentException("hot + past-hot must equal total");
        }
        archiveDeferred = true;
        if (note == null || note.isBlank()) {
            note = "365-day archive is deferred; append-only rows were not deleted";
        }
    }

    public boolean wouldArchive() {
        return pastHotCount > 0;
    }
}
