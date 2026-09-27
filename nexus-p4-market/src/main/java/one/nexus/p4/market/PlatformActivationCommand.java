package one.nexus.p4.market;

import java.util.Set;
import java.util.UUID;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.infra.isolation.IsolationContextHolder;
import one.nexus.p1.port.IsolationGuard.IsolationSnapshot;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p4.market.MarketModels.ActivationApplication;
import one.nexus.p4.market.MarketModels.ActivationApplicationStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Request-bound capability, minted only from the server's locked pending application. Never an HTTP DTO. */
public final class PlatformActivationCommand {
    private final ActivationApplication application;
    private final IsolationSnapshot snapshot;
    private final UUID contextId;
    private final MarketModels.ProductDefinition definition;
    private PlatformActivationCommand(ActivationApplication application,MarketModels.ProductDefinition definition) {
        requireOperator();
        if(application.status()!=ActivationApplicationStatus.PENDING) throw new ResponseStatusException(HttpStatus.CONFLICT,"activation is not pending");
        this.application=application; this.snapshot=IsolationContextHolder.get(); this.contextId=TenantContextHolder.require().contextId();
        this.definition=java.util.Objects.requireNonNull(definition);
    }
    static PlatformActivationCommand forPending(ActivationApplication application,MarketModels.ProductDefinition definition) { return new PlatformActivationCommand(application,definition); }
    static UUID requireOperator() {
        var snapshot=IsolationContextHolder.get(); var context=TenantContextHolder.get();
        if(context==null || context.expired(java.time.Instant.now()) || snapshot==null || snapshot.platform()==null || !snapshot.platform().active()
                || !"authenticated platform content operation".equals(snapshot.platform().reason()) || snapshot.actor()==null
                || !snapshot.actor().duties().equals(Set.of(PlatformDuty.SYSTEM_ADMIN))
                || !context.personId().equals(snapshot.actorId()) || !context.tenantId().equals(snapshot.actor().homeTenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"authenticated platform activation operator required");
        }
        return context.personId();
    }
    public void assertActive() {
        requireOperator();
        if(IsolationContextHolder.get()!=snapshot || !TenantContextHolder.require().contextId().equals(contextId))
            throw new SecurityException("activation capability is outside its issuing request");
    }
    public void assertTenant(UUID tenant) { assertActive(); if(!application.tenantId().equals(tenant)) throw new SecurityException("activation target mismatch"); }
    public void assertDefinition(MarketModels.ProductDefinition value) { assertActive(); if(!definition.equals(value)) throw new SecurityException("activation product snapshot mismatch"); }
    public ActivationApplication application() { return application; }
    public UUID actorId() { assertActive(); return snapshot.actorId(); }
    public String traceId() { assertActive(); return snapshot.traceId(); }
}
