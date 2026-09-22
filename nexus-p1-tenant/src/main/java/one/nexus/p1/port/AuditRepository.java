package one.nexus.p1.port;

import java.util.List;
import java.util.UUID;
import one.nexus.p1.domain.AuditEvent;

public interface AuditRepository {

    void insert(AuditEvent event);

    List<AuditEvent> listByTenant(UUID tenantId, int limit);

    List<AuditEvent> listByType(UUID tenantId, String eventType, int limit);
}
