package one.nexus.event;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Warns when outbox start-of-delivery lag exceeds 30s (P95 target is 5s). */
public final class DeliveryLagProbe {
    private static final Logger LOG = LoggerFactory.getLogger(DeliveryLagProbe.class);

    private final Duration warnAfter;

    public DeliveryLagProbe(Duration warnAfter) {
        this.warnAfter = warnAfter == null ? Duration.ofSeconds(30) : warnAfter;
    }

    public boolean warnIfLagging(Optional<Instant> oldestPending, Instant now) {
        if (oldestPending.isEmpty()) {
            return false;
        }
        Duration age = Duration.between(oldestPending.get(), now);
        if (age.compareTo(warnAfter) > 0) {
            LOG.warn(
                    "OUTBOX_LAG_ALARM ageMs={} thresholdMs={} oldestCreatedAt={} targetP95Ms=5000",
                    age.toMillis(),
                    warnAfter.toMillis(),
                    oldestPending.get());
            return true;
        }
        return false;
    }
}
