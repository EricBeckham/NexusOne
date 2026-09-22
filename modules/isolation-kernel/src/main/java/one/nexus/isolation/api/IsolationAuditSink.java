package one.nexus.isolation.api;

/** SPI for M0-11. Kernel tests use an in-memory sink. */
@FunctionalInterface
public interface IsolationAuditSink {
    void record(IsolationAuditEvent event);

    static IsolationAuditSink tee(IsolationAuditSink first, IsolationAuditSink second) {
        return event -> {
            first.record(event);
            second.record(event);
        };
    }
}
