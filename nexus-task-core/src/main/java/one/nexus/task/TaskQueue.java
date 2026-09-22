package one.nexus.task;

import java.time.Instant;
import java.util.UUID;

/**
 * Database task queue. Submit captures the bound M0-02 context; workers restore it
 * through {@link one.nexus.isolation.context.RequestIsolationPolicy}.
 */
public interface TaskQueue {
    UUID submit(String taskType, String payload);

    UUID submitAt(String taskType, String payload, Instant availableAt);
}
