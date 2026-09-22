package one.nexus.task;

/**
 * Optional product authorization before TaskQueue / IsolatedJobScheduler
 * enqueue. Isolation reject-before-sink still runs after this returns.
 */
public interface TaskSubmitGuard {
    void assertTask(String taskType);

    default void assertJob(String payloadType) {
        assertTask(payloadType);
    }
}
