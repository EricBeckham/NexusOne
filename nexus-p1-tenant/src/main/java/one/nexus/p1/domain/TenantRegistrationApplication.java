package one.nexus.p1.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TenantRegistrationApplication(
        UUID id,
        String orgName,
        String adminDisplayName,
        String adminEmail,
        String contactNote,
        RegistrationStatus status,
        String reviewNote,
        Instant reviewedAt,
        UUID reviewedBy,
        UUID resultingTenantId,
        Instant createdAt
) {
    public TenantRegistrationApplication {
        if (orgName == null || orgName.isBlank()) {
            throw new DomainException(ErrorCode.TENANT_INVALID_CODE, "org name is required");
        }
        if (adminDisplayName == null || adminDisplayName.isBlank()) {
            throw new DomainException(ErrorCode.TENANT_INVALID_CODE, "admin display name is required");
        }
        if (adminEmail == null || adminEmail.isBlank() || !adminEmail.contains("@")) {
            throw new DomainException(ErrorCode.TENANT_INVALID_CODE, "admin email is required");
        }
        if (status == null) {
            throw new DomainException(ErrorCode.TENANT_INVALID_STATE, "registration status is required");
        }
    }

    public static TenantRegistrationApplication pending(
            UUID id, String orgName, String adminDisplayName, String adminEmail, String contactNote, Instant now) {
        return new TenantRegistrationApplication(
                id,
                orgName.trim(),
                adminDisplayName.trim(),
                adminEmail.trim().toLowerCase(),
                blankToNull(contactNote),
                RegistrationStatus.PENDING,
                null,
                null,
                null,
                null,
                now);
    }

    public TenantRegistrationApplication approve(UUID tenantId, Instant now, UUID actorId, String note) {
        requirePending();
        return new TenantRegistrationApplication(
                id, orgName, adminDisplayName, adminEmail, contactNote,
                RegistrationStatus.APPROVED, blankToNull(note), now, actorId, tenantId, createdAt);
    }

    public TenantRegistrationApplication reject(Instant now, UUID actorId, String note) {
        requirePending();
        if (note == null || note.isBlank()) {
            throw new DomainException(ErrorCode.TENANT_INVALID_CODE, "reject note is required",
                    Map.of("application_id", id.toString()));
        }
        return new TenantRegistrationApplication(
                id, orgName, adminDisplayName, adminEmail, contactNote,
                RegistrationStatus.REJECTED, note.trim(), now, actorId, null, createdAt);
    }

    private void requirePending() {
        if (status != RegistrationStatus.PENDING) {
            throw new DomainException(
                    ErrorCode.TENANT_INVALID_STATE,
                    "registration is not pending",
                    Map.of("application_id", id.toString(), "status", status.name()));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}