package one.nexus.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Minimal runnable stub for the 90-day hot retention policy.
 *
 * <p>Reads the existing hash chain through {@link AuditPort#chain()} and
 * classifies rows as hot ({@code occurred_at >= now - 90d}) or past-hot.
 * It never issues {@code DELETE} / {@code UPDATE} on {@code platform.audit_record}
 * (append-only + {@code REVOKE}). It never copies rows to a 365-day archive.
 *
 * <p>Same-TX {@link AuditPort#append} / {@link AuditPort#appendNewTransaction}
 * rules are unchanged. Isolated Quartz scheduling is not registered at boot
 * (that API requires a tenant membership); product code exposes this bean
 * and a Task handler {@value #TASK_TYPE}.
 */
public final class AuditHotRetentionJob {
    public static final String TASK_TYPE = "audit.retention.hot-scan";

    private final AuditPort port;
    private final Clock clock;

    public AuditHotRetentionJob(AuditPort port) {
        this(port, Clock.systemUTC());
    }

    public AuditHotRetentionJob(AuditPort port, Clock clock) {
        this.port = Objects.requireNonNull(port, "port");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public AuditRetentionScan scan() {
        return scan(clock.instant());
    }

    public AuditRetentionScan scan(Instant now) {
        Instant scannedAt = now == null ? clock.instant() : now;
        Instant cutoff = AuditRetentionPolicy.hotCutoff(scannedAt);
        List<ChainedAuditEvent> events = port.chain();
        long hot = 0;
        long pastHot = 0;
        for (ChainedAuditEvent linked : events) {
            if (AuditRetentionPolicy.isHot(linked.event().occurredAt(), scannedAt)) {
                hot++;
            } else {
                pastHot++;
            }
        }
        return new AuditRetentionScan(
                scannedAt,
                cutoff,
                hot,
                pastHot,
                events.size(),
                true,
                "365-day archive is deferred; append-only rows were not deleted");
    }
}
