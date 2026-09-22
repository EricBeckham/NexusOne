package one.nexus.event;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Start-of-delivery lag: package SLO is P95 ≤ 5s. Dispatcher logs
 * {@code OUTBOX_LAG_ALARM} when the oldest {@code PENDING}/{@code FAILED} row
 * is older than {@code lag-warn-after} (product default 30s).
 */
public final class DeliveryLagProbe {
    public static final long TARGET_P95_MS = 5_000;
    private static final Logger LOG = LoggerFactory.getLogger(DeliveryLagProbe.class);

    private final Duration warnAfter;
    private final ConcurrentLinkedQueue<Long> startAgesMs = new ConcurrentLinkedQueue<>();
    private final AtomicReference<Duration> lastAlarmAge = new AtomicReference<>();

    public DeliveryLagProbe(Duration warnAfter) {
        this.warnAfter = warnAfter == null ? Duration.ofSeconds(30) : warnAfter;
    }

    public Duration warnAfter() {
        return warnAfter;
    }

    public boolean warnIfLagging(Optional<Instant> oldestPending, Instant now) {
        if (oldestPending.isEmpty()) {
            lastAlarmAge.set(null);
            return false;
        }
        Duration age = Duration.between(oldestPending.get(), now);
        if (age.compareTo(warnAfter) > 0) {
            lastAlarmAge.set(age);
            LOG.warn(
                    "OUTBOX_LAG_ALARM ageMs={} thresholdMs={} oldestCreatedAt={} targetP95Ms={}",
                    age.toMillis(),
                    warnAfter.toMillis(),
                    oldestPending.get(),
                    TARGET_P95_MS);
            return true;
        }
        lastAlarmAge.set(null);
        return false;
    }

    public void recordStartOfDelivery(Instant createdAt, Instant startedAt) {
        if (createdAt == null || startedAt == null) {
            return;
        }
        startAgesMs.add(Duration.between(createdAt, startedAt).toMillis());
    }

    public Optional<Duration> lastAlarmAge() {
        return Optional.ofNullable(lastAlarmAge.get());
    }

    public OptionalLong p95Ms() {
        long[] sorted = startAgesMs.stream().mapToLong(Long::longValue).sorted().toArray();
        if (sorted.length == 0) {
            return OptionalLong.empty();
        }
        int index = (int) Math.ceil(sorted.length * 0.95d) - 1;
        index = Math.max(0, Math.min(index, sorted.length - 1));
        return OptionalLong.of(sorted[index]);
    }

    public int sampleCount() {
        return startAgesMs.size();
    }
}
