package one.nexus.p1.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record Tenant(
        UUID id,
        String code,
        String name,
        TenantKind kind,
        TenantStatus status,
        String legalName,
        Instant createdAt,
        UUID createdBy,
        Instant updatedAt,
        UUID updatedBy,
        Instant deletedAt,
        long version
) {

    public Tenant {
        TenantCode.requireValid(code);
        if (kind == TenantKind.PLATFORM && !TenantCode.PLATFORM.equals(code)) {
            throw new DomainException(ErrorCode.TENANT_INVALID_CODE, "platform tenant code must be 'platform'");
        }
        if (name == null || name.isBlank()) {
            throw new DomainException(ErrorCode.TENANT_INVALID_CODE, "tenant name is required");
        }
    }

    public boolean platform() {
        return kind == TenantKind.PLATFORM;
    }

    public boolean enabled() {
        return status == TenantStatus.ENABLED && deletedAt == null;
    }

    public Tenant enable(Instant now, UUID actorId) {
        requireNotDeleted();
        if (status == TenantStatus.ENABLED) {
            return this;
        }
        return withStatus(TenantStatus.ENABLED, now, actorId);
    }

    public Tenant disable(Instant now, UUID actorId) {
        requireNotDeleted();
        if (platform()) {
            throw new DomainException(
                    ErrorCode.TENANT_PLATFORM_PROTECTED,
                    "reserved platform tenant cannot be disabled",
                    Map.of("tenant_id", id.toString(), "code", code, "kind", kind.name()));
        }
        if (status == TenantStatus.DISABLED) {
            return this;
        }
        return withStatus(TenantStatus.DISABLED, now, actorId);
    }

    public Tenant rename(String newName, String newLegalName, Instant now, UUID actorId) {
        requireNotDeleted();
        TenantCode.requireNotBlankName(newName);
        return new Tenant(
                id, code, newName.trim(), kind, status, newLegalName,
                createdAt, createdBy, now, actorId, deletedAt, version + 1);
    }

    private void requireNotDeleted() {
        if (deletedAt != null) {
            throw new DomainException(ErrorCode.TENANT_INVALID_STATE, "tenant is deleted");
        }
    }

    private Tenant withStatus(TenantStatus next, Instant now, UUID actorId) {
        return new Tenant(
                id, code, name, kind, next, legalName,
                createdAt, createdBy, now, actorId, deletedAt, version + 1);
    }
}
