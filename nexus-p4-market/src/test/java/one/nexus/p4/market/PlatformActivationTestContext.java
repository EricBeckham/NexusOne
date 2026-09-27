package one.nexus.p4.market;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import one.nexus.p1.domain.Actor;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.infra.isolation.IsolationContextHolder;
import one.nexus.p1.port.PlatformContext;
import one.nexus.p1.port.IsolationGuard.IsolationSnapshot;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
final class PlatformActivationTestContext {
    static void bind() {
        UUID home=UUID.randomUUID(),person=UUID.randomUUID();
        var actor=new Actor(person,home,"Platform",Set.of(PlatformDuty.SYSTEM_ADMIN));
        TenantContextHolder.set(new TenantContext(UUID.randomUUID(),home,UUID.randomUUID(),person,"test",Instant.now(),Instant.now().plusSeconds(300)));
        IsolationContextHolder.set(new IsolationSnapshot(actor,new PlatformContext(true,"authenticated platform content operation",actor,"test-trace",home),null,"test-trace"));
    }
    static void clear(){TenantContextHolder.clear();IsolationContextHolder.clear();}
}
