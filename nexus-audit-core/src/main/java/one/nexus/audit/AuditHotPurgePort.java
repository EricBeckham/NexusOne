package one.nexus.audit;

import java.time.Instant;

/**
 * Safe 90-day purge of the hot hash-chain table. Implementations DELETE only
 * a contiguous prefix of {@code platform.audit_record} that is already
 * cold-archived or otherwise eligible (same-PG archive copy still inside the
 * 365-day window). They must not DROP the hot table, must not UPDATE rows, and
 * must not change {@link AuditPort#append} / {@link AuditPort#appendNewTransaction}.
 */
public interface AuditHotPurgePort {
    HotPurgeRunResult purgeEligibleHot(Instant now);
}
