package one.nexus.isolation.api;

/** SPI for M0-11. Kernel tests use an in-memory sink. */
@FunctionalInterface
public interface IsolationAuditSink {
    void record(IsolationAuditEvent event);
}
