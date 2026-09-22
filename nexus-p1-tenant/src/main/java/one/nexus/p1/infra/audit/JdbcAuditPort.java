package one.nexus.p1.infra.audit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import one.nexus.p1.domain.AuditEvent;
import one.nexus.p1.port.AuditPort;
import one.nexus.p1.port.AuditRepository;

@Component
public class JdbcAuditPort implements AuditPort {

    private final AuditRepository repository;

    public JdbcAuditPort(AuditRepository repository) {
        this.repository = repository;
    }

    @Override
    public void append(AuditEvent event) {
        repository.insert(event);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void appendNewTransaction(AuditEvent event) {
        repository.insert(event);
    }
}
