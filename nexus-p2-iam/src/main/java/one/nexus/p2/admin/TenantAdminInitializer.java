package one.nexus.p2.admin;

import java.util.UUID;

/**
 * In-process API for M0-03 tenant lifecycle: create the first tenant admin user,
 * membership, root department, default position and primary employment.
 */
public interface TenantAdminInitializer {
    record Command(UUID tenantId, String displayName, String email, String username) {}

    record Result(
            UUID tenantId,
            UUID accountId,
            UUID personId,
            UUID membershipId,
            UUID departmentId,
            UUID positionId,
            UUID employmentId,
            String keycloakUserId
    ) {}

    Result initialize(Command command);
}
