package one.nexus.isolation.audit;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import one.nexus.isolation.api.IsolationAuditEvent;
import one.nexus.isolation.api.IsolationAuditSink;

public final class CollectingAuditSink implements IsolationAuditSink {
    private final List<IsolationAuditEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void record(IsolationAuditEvent event) {
        events.add(event);
    }

    public List<IsolationAuditEvent> events() {
        return List.copyOf(events);
    }

    public long rejectCount() {
        return events.stream().filter(event -> IsolationAuditEvent.ISOLATION_REJECT.equals(event.type())).count();
    }
}
