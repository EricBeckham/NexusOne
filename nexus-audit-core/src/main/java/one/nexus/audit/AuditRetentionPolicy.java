package one.nexus.audit;

import java.time.Duration;
import java.time.Instant;

/**
 * G4 retention targets from ARCH-008 / ARCH-CMP-003.
 *
 * <p>Hot default is 90 days. Archive default is 365 days. The archive Job copies
 * hot rows older than {@link #HOT_RETENTION} into {@code platform.audit_record_archive}
 * and deletes archive rows older than {@link #ARCHIVE_RETENTION} ({@code occurred_at}).
 * Object-store cold archive is a separate {@link AuditColdArchiveJob} copy.
 * {@link AuditHotPurgeJob} may DELETE a hot prefix that is already cold-archived
 * or still safely present in the 365-day same-PG archive. It does not DROP
 * {@code platform.audit_record}.
 */
public final class AuditRetentionPolicy {
    public static final Duration HOT_RETENTION = Duration.ofDays(90);
    public static final Duration ARCHIVE_RETENTION = Duration.ofDays(365);

    private AuditRetentionPolicy() {}

    public static Instant hotCutoff(Instant now) {
        return now.minus(HOT_RETENTION);
    }

    public static Instant archiveExpiry(Instant now) {
        return now.minus(ARCHIVE_RETENTION);
    }
}
