package one.nexus.audit;

import java.time.Duration;
import java.time.Instant;

/**
 * G4 retention targets from ARCH-008 / ARCH-CMP-003.
 *
 * <p>Hot default is 90 days. Archive default is 365 days. The archive Job copies
 * hot rows older than {@link #HOT_RETENTION} into {@code platform.audit_record_archive}.
 * It does <strong>not</strong> delete from the append-only hot table, does
 * <strong>not</strong> write object-store cold storage, and does <strong>not</strong>
 * purge the archive after 365 days.
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
