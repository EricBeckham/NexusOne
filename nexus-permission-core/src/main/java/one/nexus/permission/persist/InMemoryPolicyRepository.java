package one.nexus.permission.persist;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import one.nexus.common.id.UuidV7;
import one.nexus.permission.engine.PolicySnapshot;
import one.nexus.permission.engine.Predicate;
import one.nexus.permission.engine.Rule;
import one.nexus.permission.support.PermissionException;

public final class InMemoryPolicyRepository implements PolicyRepository {
    private final Map<UUID, RoleRecord> roles = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> roleParents = new ConcurrentHashMap<>();
    private final Map<UUID, AssignmentRecord> assignments = new ConcurrentHashMap<>();
    private final Map<UUID, GrantRecord> grants = new ConcurrentHashMap<>();
    private final Map<UUID, ShareRecord> shares = new ConcurrentHashMap<>();
    private final Map<UUID, Long> versions = new ConcurrentHashMap<>();

    @Override
    public RoleRecord saveRole(RoleRecord role) {
        findRoleByCode(role.tenantId(), role.code()).ifPresent(existing -> {
            if (!existing.id().equals(role.id())) {
                throw PermissionException.conflict("role code already exists in this tenant");
            }
        });
        roles.put(role.id(), role);
        versions.putIfAbsent(role.tenantId(), 1L);
        return role;
    }

    @Override
    public Optional<RoleRecord> findRole(UUID tenantId, UUID roleId) {
        return Optional.ofNullable(roles.get(roleId)).filter(item -> item.tenantId().equals(tenantId));
    }

    @Override
    public Optional<RoleRecord> findRoleByCode(UUID tenantId, String code) {
        return roles.values().stream()
                .filter(item -> item.tenantId().equals(tenantId) && item.code().equals(code))
                .findFirst();
    }

    @Override
    public List<RoleRecord> listRoles(UUID tenantId) {
        return roles.values().stream().filter(item -> item.tenantId().equals(tenantId)).toList();
    }

    @Override
    public void addRoleParent(UUID tenantId, UUID childRoleId, UUID parentRoleId) {
        RoleRecord child = findRole(tenantId, childRoleId).orElseThrow(() -> PermissionException.notFound("child role"));
        RoleRecord parent = findRole(tenantId, parentRoleId).orElseThrow(() -> PermissionException.notFound("parent role"));
        roleParents.computeIfAbsent(key(tenantId, child.id()), ignored -> ConcurrentHashMap.newKeySet()).add(parent.id());
    }

    @Override
    public AssignmentRecord saveAssignment(AssignmentRecord assignment) {
        assignments.put(assignment.id(), assignment);
        return assignment;
    }

    @Override
    public List<AssignmentRecord> listAssignments(UUID tenantId) {
        return assignments.values().stream().filter(item -> item.tenantId().equals(tenantId)).toList();
    }

    @Override
    public boolean deleteAssignment(UUID tenantId, UUID assignmentId) {
        AssignmentRecord existing = assignments.get(assignmentId);
        if (existing == null || !existing.tenantId().equals(tenantId)) {
            return false;
        }
        return assignments.remove(assignmentId, existing);
    }

    @Override
    public Set<String> roleCodesFor(UUID tenantId, String userId, Set<String> positionIds, Set<String> departmentIds) {
        Set<String> codes = new LinkedHashSet<>();
        for (AssignmentRecord item : listAssignments(tenantId)) {
            boolean match = switch (item.principalType()) {
                case "user" -> item.principalId().equals(userId);
                case "position" -> positionIds != null && positionIds.contains(item.principalId());
                case "department" -> departmentIds != null && departmentIds.contains(item.principalId());
                default -> false;
            };
            if (match) {
                codes.add(item.roleCode());
            }
        }
        return codes;
    }

    @Override
    public GrantRecord saveGrant(GrantRecord grant) {
        grants.put(grant.id(), grant);
        return grant;
    }

    @Override
    public List<GrantRecord> listGrants(UUID tenantId) {
        return grants.values().stream().filter(item -> item.tenantId().equals(tenantId)).toList();
    }

    @Override
    public boolean deleteGrant(UUID tenantId, UUID grantId) {
        GrantRecord existing = grants.get(grantId);
        if (existing == null || !existing.tenantId().equals(tenantId)) {
            return false;
        }
        return grants.remove(grantId, existing);
    }

    @Override
    public ShareRecord saveShare(ShareRecord share) {
        shares.put(share.id(), share);
        return share;
    }

    @Override
    public List<Predicate> recordShares(UUID tenantId, String resource, Set<String> principalKeys) {
        List<String> ids = new ArrayList<>();
        for (ShareRecord share : shares.values()) {
            if (!share.tenantId().equals(tenantId) || !share.resource().equals(resource)) {
                continue;
            }
            if (principalKeys.contains(share.principalType() + ":" + share.principalId())) {
                ids.add(share.recordId());
            }
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        return List.of(Predicate.atom("id", "IN", ids));
    }

    @Override
    public long currentVersion(UUID tenantId) {
        return versions.getOrDefault(tenantId, 1L);
    }

    @Override
    public long bumpVersion(UUID tenantId) {
        return versions.merge(tenantId, 1L, (previous, ignored) -> previous + 1);
    }

    @Override
    public PolicySnapshot loadSnapshot(UUID tenantId) {
        List<Rule> grantRules = new ArrayList<>();
        List<Rule> denyRules = new ArrayList<>();
        for (GrantRecord grant : listGrants(tenantId)) {
            if ("DENY".equalsIgnoreCase(grant.effect())) {
                denyRules.add(grant.rule());
            } else {
                grantRules.add(grant.rule());
            }
        }
        Map<String, Set<String>> parents = new LinkedHashMap<>();
        for (RoleRecord role : listRoles(tenantId)) {
            Set<UUID> parentIds = roleParents.getOrDefault(key(tenantId, role.id()), Set.of());
            if (parentIds.isEmpty()) {
                continue;
            }
            Set<String> codes = new LinkedHashSet<>();
            for (UUID parentId : parentIds) {
                findRole(tenantId, parentId).ifPresent(parent -> codes.add(parent.code()));
            }
            parents.put(role.code(), codes);
        }
        return new PolicySnapshot(
                tenantId.toString(),
                currentVersion(tenantId),
                grantRules,
                denyRules,
                parents,
                Instant.now());
    }

    public static String key(UUID tenantId, UUID roleId) {
        return tenantId + ":" + roleId;
    }

    public static RoleRecord newRole(UUID tenantId, String code, String name, boolean systemRole) {
        return new RoleRecord(UuidV7.generate(), tenantId, code, name, systemRole, "ACTIVE");
    }
}
