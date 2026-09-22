package one.nexus.p1.audit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import one.nexus.p1.domain.AuditEvent;
import one.nexus.p1.port.AuditRepository;

/** Staging-like in-memory stand-in for {@code platform.audit_event}. Test harness only. */
final class InMemoryAuditRepository implements AuditRepository {

    private final List<AuditEvent> rows = new ArrayList<>();

    @Override
    public void insert(AuditEvent event) {
        rows.add(event);
    }

    @Override
    public List<AuditEvent> listByTenant(UUID tenantId, int limit) {
        return rows.stream()
                .filter(event -> tenantId.equals(event.tenantId()))
                .sorted(Comparator.comparing(AuditEvent::occurredAt).reversed())
                .limit(Math.max(limit, 0))
                .toList();
    }

    @Override
    public List<AuditEvent> listByType(UUID tenantId, String eventType, int limit) {
        return rows.stream()
                .filter(event -> tenantId.equals(event.tenantId()) && eventType.equals(event.eventType()))
                .sorted(Comparator.comparing(AuditEvent::occurredAt).reversed())
                .limit(Math.max(limit, 0))
                .toList();
    }
}
