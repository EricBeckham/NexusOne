package one.nexus.p2.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class Model {
    private Model() {}

    public enum AccountStatus { ACTIVE, DISABLED, LOCKED }
    public enum PersonStatus { ACTIVE, DISABLED, LEFT }
    public enum MembershipStatus { ACTIVE, SUSPENDED }
    public enum OrgStatus { ACTIVE, DISABLED }
    public enum EmploymentKind { PRIMARY, SECONDARY }
    public enum EmploymentStatus { ACTIVE, ENDED }

    public record GlobalAccount(
            UUID id,
            String keycloakUserId,
            String email,
            String phone,
            String username,
            String displayName,
            AccountStatus status,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt,
            int version
    ) {}

    public record TenantMembership(
            UUID id,
            UUID tenantId,
            UUID accountId,
            MembershipStatus status,
            Instant joinedAt,
            Instant updatedAt,
            Instant deletedAt,
            int version
    ) {}

    public record Person(
            UUID id,
            UUID tenantId,
            UUID accountId,
            String displayName,
            String employeeNo,
            String email,
            String phone,
            LocalDate hiredOn,
            PersonStatus status,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt,
            int version
    ) {}

    public record Department(
            UUID id,
            UUID tenantId,
            UUID parentId,
            String code,
            String name,
            String path,
            int sortOrder,
            UUID leaderPersonId,
            OrgStatus status,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt,
            int version
    ) {}

    public record Position(
            UUID id,
            UUID tenantId,
            String code,
            String name,
            String category,
            String duties,
            OrgStatus status,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt,
            int version
    ) {}

    public record Employment(
            UUID id,
            UUID tenantId,
            UUID personId,
            UUID departmentId,
            UUID positionId,
            EmploymentKind kind,
            EmploymentStatus status,
            LocalDate validFrom,
            LocalDate validTo,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt,
            int version
    ) {}

    public record IssuedContext(
            UUID id,
            UUID tenantId,
            UUID accountId,
            UUID personId,
            String idpSubject,
            Instant issuedAt,
            Instant expiresAt,
            Instant revokedAt
    ) {}

    /** Tenant-scoped IdP metadata. Secrets are deliberately held outside the model. */
    public record IdpConfiguration(
            UUID id,
            UUID tenantId,
            String providerType,
            String displayName,
            String clientId,
            String issuer,
            String status,
            Instant lastSyncAt,
            Boolean lastSyncOk,
            int syncFailCount,
            Instant deletedAt,
            Instant createdAt,
            Instant updatedAt,
            int version
    ) {}
}
