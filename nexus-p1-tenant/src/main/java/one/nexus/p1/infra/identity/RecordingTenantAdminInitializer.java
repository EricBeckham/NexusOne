package one.nexus.p1.infra.identity;

import org.springframework.stereotype.Component;
import one.nexus.p1.port.ClockPort;
import one.nexus.p1.port.IdGenerator;
import one.nexus.p1.port.TenantAdminInitializer;

/**
 * M0-04 stub. Records the initialization command as ACCEPTED. Does not call Keycloak.
 */
@Component
public class RecordingTenantAdminInitializer implements TenantAdminInitializer {

    private final IdGenerator ids;
    private final ClockPort clock;

    public RecordingTenantAdminInitializer(IdGenerator ids, ClockPort clock) {
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public AdminInitResult initialize(AdminInitCommand command) {
        return new AdminInitResult(
                ids.next(),
                AdminInitResult.Status.ACCEPTED,
                "queued for M0-04 IAM; Keycloak is out of scope for P1. recorded_at=" + clock.now());
    }
}
