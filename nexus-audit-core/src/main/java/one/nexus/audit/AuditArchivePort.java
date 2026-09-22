package one.nexus.audit;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Copy path from hot {@code platform.audit_record} toward a 365-day archive.
 * Implementations must INSERT only on the hot chain. They must not UPDATE or
 * DELETE {@code platform.audit_record}. Archive expiry may DELETE (or equivalent
 * remove) rows from the archive store only.
 */
public interface AuditArchivePort {
    ArchiveRunResult copyHotOlderThan(Duration hotRetention, Instant now);

    List<ChainedAuditEvent> findArchivedByTraceId(String traceId);

    /** All same-PG archive rows. Used as the source for object-store cold copy. */
    List<ChainedAuditEvent> findArchived();

    /**
     * Remove archive-store rows whose {@code occurred_at} is older than
     * {@code now - archiveRetention}. Hot chain is untouched. Same-PG delete is
     * the implemented path. Object-store cold archive is a separate copy.
     */
    ArchiveRunResult purgeExpiredArchive(Duration archiveRetention, Instant now);
}
