package one.nexus.event;

import java.time.Duration;
import java.util.UUID;

/**
 * Reclaim expired leases and re-queue dead letters. Quartz should trigger this
 * on a platform path ({@code exec.reconcile}); do not start a business Timer.
 */
public interface ReconciliationPort {
    int reclaimExpiredLeases();

    int republishStuck(Duration olderThan);

    boolean replayDead(UUID deadLetterId);
}
