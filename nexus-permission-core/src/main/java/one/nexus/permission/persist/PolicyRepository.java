package one.nexus.permission.persist;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import one.nexus.permission.engine.PolicySnapshot;
import one.nexus.permission.engine.Predicate;
import one.nexus.permission.engine.Rule;

public interface PolicyRepository {
    RoleRecord saveRole(RoleRecord role);

    Optional<RoleRecord> findRole(UUID tenantId, UUID roleId);

    Optional<RoleRecord> findRoleByCode(UUID tenantId, String code);

    List<RoleRecord> listRoles(UUID tenantId);

    void addRoleParent(UUID tenantId, UUID childRoleId, UUID parentRoleId);

    AssignmentRecord saveAssignment(AssignmentRecord assignment);

    List<AssignmentRecord> listAssignments(UUID tenantId);

    Set<String> roleCodesFor(UUID tenantId, String userId, Set<String> positionIds, Set<String> departmentIds);

    GrantRecord saveGrant(GrantRecord grant);

    List<GrantRecord> listGrants(UUID tenantId);

    /** PV4: soft-removes one grant of this tenant; returns false when it was not there. */
    boolean deleteGrant(UUID tenantId, UUID grantId);

    ShareRecord saveShare(ShareRecord share);

    List<Predicate> recordShares(UUID tenantId, String resource, Set<String> principalKeys);

    long currentVersion(UUID tenantId);

    long bumpVersion(UUID tenantId);

    PolicySnapshot loadSnapshot(UUID tenantId);

    record RoleRecord(UUID id, UUID tenantId, String code, String name, boolean systemRole, String status) {}

    record AssignmentRecord(
            UUID id, UUID tenantId, String principalType, String principalId, UUID roleId, String roleCode) {}

    record GrantRecord(UUID id, UUID tenantId, String effect, Rule rule) {}

    record ShareRecord(
            UUID id, UUID tenantId, String resource, String recordId, String principalType, String principalId) {}
}
