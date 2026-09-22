package one.nexus.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Platform archive Job: copy hot rows older than 90 days, then purge archive
 * rows older than 365 days. Not a tenant {@code IsolatedQuartzJob} (that API
 * requires membership). Product wires a {@code TaskHandler} / opt-in Quartz
 * trigger that issues {@code PlatformContext} path {@link #PLATFORM_PATH} and
 * calls {@link #run()}.
 */
public final class AuditArchiveJob {
    public static final String TASK_TYPE = "audit.archive.copy";
    public static final String PLATFORM_PATH = "audit.archive";

    private final AuditArchivePort archive;
    private final Clock clock;
    private volatile ArchiveRunResult last;

    public AuditArchiveJob(AuditArchivePort archive, Clock clock) {
        this.archive = Objects.requireNonNull(archive, "archive");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public ArchiveRunResult run() {
        Instant now = clock.instant();
        ArchiveRunResult copy = archive.copyHotOlderThan(AuditRetentionPolicy.HOT_RETENTION, now);
        ArchiveRunResult purge = archive.purgeExpiredArchive(AuditRetentionPolicy.ARCHIVE_RETENTION, now);
        last = copy.withPurge(purge);
        return last;
    }

    public ArchiveRunResult lastResult() {
        return last;
    }

    public AuditArchivePort archive() {
        return archive;
    }
}
