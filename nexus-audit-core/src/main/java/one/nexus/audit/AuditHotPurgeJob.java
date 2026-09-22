package one.nexus.audit;

import java.time.Clock;
import java.util.Objects;

/**
 * Platform 90-day hot-prefix purge Job. Reuses {@link AuditArchiveJob#PLATFORM_PATH}.
 * Not a tenant {@code IsolatedQuartzJob}. Does not copy (archive / cold already
 * do) and does not DROP {@code platform.audit_record}.
 */
public final class AuditHotPurgeJob {
    public static final String TASK_TYPE = "audit.hot.purge";
    public static final String PLATFORM_PATH = AuditArchiveJob.PLATFORM_PATH;

    private final AuditHotPurgePort purge;
    private final Clock clock;
    private volatile HotPurgeRunResult last;

    public AuditHotPurgeJob(AuditHotPurgePort purge, Clock clock) {
        this.purge = Objects.requireNonNull(purge, "purge");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public HotPurgeRunResult run() {
        last = purge.purgeEligibleHot(clock.instant());
        return last;
    }

    public HotPurgeRunResult lastResult() {
        return last;
    }

    public AuditHotPurgePort purge() {
        return purge;
    }
}
