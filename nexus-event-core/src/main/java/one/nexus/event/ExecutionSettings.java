package one.nexus.event;

import java.time.Duration;

/** Shared lease / retry / lag settings for outbox and task workers. */
public record ExecutionSettings(
        Duration pollInterval,
        Duration leaseDuration,
        Duration lagWarnAfter,
        int batchSize,
        int maxAttempts
) {
    public ExecutionSettings {
        pollInterval = pollInterval == null ? Duration.ofMillis(500) : pollInterval;
        leaseDuration = leaseDuration == null ? Duration.ofSeconds(30) : leaseDuration;
        lagWarnAfter = lagWarnAfter == null ? Duration.ofSeconds(30) : lagWarnAfter;
        if (batchSize < 1) {
            batchSize = 16;
        }
        if (maxAttempts < 1) {
            maxAttempts = 5;
        }
    }

    public static ExecutionSettings defaults() {
        return new ExecutionSettings(
                Duration.ofMillis(500),
                Duration.ofSeconds(30),
                Duration.ofSeconds(30),
                16,
                5);
    }

    public Duration backoff(int attempt) {
        int exp = Math.min(Math.max(attempt, 1), 8);
        return Duration.ofSeconds(1L << (exp - 1));
    }
}
