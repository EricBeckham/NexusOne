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

    /**
     * Remove archive-store rows whose {@code occurred_at} is older than
     * {@code now - archiveRetention}. Hot chain is untouched. Same-PG delete is
     * the implemented path; object-store cold archive is out of scope.
     */
    ArchiveRunResult purgeExpiredArchive(Duration archiveRetention, Instant now);
}
