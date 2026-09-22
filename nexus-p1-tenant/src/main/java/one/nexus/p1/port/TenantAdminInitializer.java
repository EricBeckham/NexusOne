package one.nexus.p1.port;

import java.util.UUID;
import one.nexus.p1.domain.Actor;

/**
 * M0-04 / P2 hook. P1 creates the tenant then asks IAM to provision the first tenant admin
 * (person + employment + Keycloak organization membership). This module does not call Keycloak.
 *
 * <p>Until M0-04 implements this port, {@code RecordingTenantAdminInitializer} persists an
 * {@code ACCEPTED} request. M0-04 should replace the bean and may set {@code COMPLETED} or
 * {@code FAILED} after reconciliation.
 */
public interface TenantAdminInitializer {

    AdminInitResult initialize(AdminInitCommand command);

    record AdminInitCommand(
            UUID tenantId,
            String tenantCode,
            String adminDisplayName,
            String adminEmail,
            Actor actor,
            String traceId,
            String correlationId
    ) {}

    record AdminInitResult(UUID requestId, Status status, String message) {
        public enum Status {
            ACCEPTED,
            COMPLETED,
            FAILED
        }
    }
}
