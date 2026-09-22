package one.nexus.permission.engine;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Tenant-scoped loaded policy. Distinct from {@link one.nexus.common.permission.PolicySnapshot}
 * which is the cross-module version handle.
 */
public record PolicySnapshot(
        String tenantId,
        long version,
        List<Rule> grants,
        List<Rule> denies,
        Map<String, Set<String>> roleParents,
        Instant generatedAt
) {
    public PolicySnapshot {
        Objects.requireNonNull(tenantId, "tenantId");
        if (version < 1) {
            throw new IllegalArgumentException("version must be >= 1");
        }
        grants = List.copyOf(grants == null ? List.of() : grants);
        denies = List.copyOf(denies == null ? List.of() : denies);
        Map<String, Set<String>> copied = new LinkedHashMap<>();
        if (roleParents != null) {
            roleParents.forEach((child, parents) -> copied.put(child, Set.copyOf(parents)));
        }
        roleParents = Map.copyOf(copied);
        generatedAt = generatedAt == null ? Instant.EPOCH : generatedAt;
    }

    public PolicySnapshot(String tenantId, long version, List<Rule> grants, List<Rule> denies,
                          Map<String, Set<String>> roleParents) {
        this(tenantId, version, grants, denies, roleParents, Instant.EPOCH);
    }

    public PolicySnapshot(String tenantId, long version, List<Rule> grants) {
        this(tenantId, version, grants, List.of(), Map.of());
    }

    public PolicySnapshot(String tenantId, long version) {
        this(tenantId, version, List.of(), List.of(), Map.of());
    }

    public one.nexus.common.permission.PolicySnapshot toHandle() {
        UUID id;
        try {
            id = UUID.fromString(tenantId);
        } catch (IllegalArgumentException ex) {
            id = UUID.nameUUIDFromBytes(tenantId.getBytes());
        }
        Instant at = generatedAt.equals(Instant.EPOCH) ? Instant.now() : generatedAt;
        return new one.nexus.common.permission.PolicySnapshot(id, version, at);
    }
}
