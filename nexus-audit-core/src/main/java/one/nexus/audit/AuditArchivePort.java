package one.nexus.audit;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Copy path from hot {@code platform.audit_record} toward a 365-day archive.
 * Implementations must INSERT only. They must not UPDATE or DELETE the hot chain.
 */
public interface AuditArchivePort {
    ArchiveRunResult copyHotOlderThan(Duration hotRetention, Instant now);

    List<ChainedAuditEvent> findArchivedByTraceId(String traceId);

    /**
     * 365-day archive expiry is not implemented. Default returns a zero-copy
     * result with {@code purgeImplemented=false}.
     */
    default ArchiveRunResult purgeExpiredArchive(Duration archiveRetention, Instant now) {
        return new ArchiveRunResult(0, 0, now.minus(archiveRetention), false);
    }
}
