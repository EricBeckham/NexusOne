package one.nexus.audit;

import java.time.Duration;
import java.time.Instant;

/**
 * ARCH-008 / ARCH-CMP-003: audit hot data 90 days, archive 365 days. The two
 * windows are not the application-log cleanup policy.
 *
 * <p>This class documents the numbers. The runnable stub
 * ({@link AuditHotRetentionJob}) classifies hot vs past-hot and does
 * <strong>not</strong> delete {@code platform.audit_record} (append-only) and
 * does <strong>not</strong> copy to a 365-day archive (deferred).
 */
public final class AuditRetentionPolicy {
    public static final int HOT_DAYS = 90;
    public static final int ARCHIVE_DAYS = 365;
    public static final Duration HOT_RETENTION = Duration.ofDays(HOT_DAYS);
    public static final Duration ARCHIVE_RETENTION = Duration.ofDays(ARCHIVE_DAYS);
    public static final boolean ARCHIVE_IMPLEMENTED = false;

    private AuditRetentionPolicy() {}

    public static Instant hotCutoff(Instant now) {
        Instant instant = now == null ? Instant.now() : now;
        return instant.minus(HOT_RETENTION);
    }

    public static boolean isHot(Instant occurredAt, Instant now) {
        if (occurredAt == null) {
            return false;
        }
        return !occurredAt.isBefore(hotCutoff(now));
    }
}
