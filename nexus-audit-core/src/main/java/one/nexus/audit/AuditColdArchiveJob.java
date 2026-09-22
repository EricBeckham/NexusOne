package one.nexus.audit;

import java.time.Clock;
import java.util.Objects;

/**
 * Platform cold-archive Job: copy aged same-PG archive (or archive-eligible
 * hot rows) to object storage. Reuses {@link AuditArchiveJob#PLATFORM_PATH}.
 * Not a tenant {@code IsolatedQuartzJob}.
 */
public final class AuditColdArchiveJob {
    public static final String TASK_TYPE = "audit.archive.cold";
    public static final String PLATFORM_PATH = AuditArchiveJob.PLATFORM_PATH;

    private final AuditColdArchivePort cold;
    private final Clock clock;
    private volatile ColdArchiveRunResult last;

    public AuditColdArchiveJob(AuditColdArchivePort cold, Clock clock) {
        this.cold = Objects.requireNonNull(cold, "cold");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public ColdArchiveRunResult run() {
        last = cold.copyEligible(clock.instant());
        return last;
    }

    public ColdArchiveRunResult lastResult() {
        return last;
    }

    public AuditColdArchivePort cold() {
        return cold;
    }
}
