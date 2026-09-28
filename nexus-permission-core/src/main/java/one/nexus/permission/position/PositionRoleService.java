package one.nexus.permission.position;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.domain.Model;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.PlatformDutyCatalog;
import one.nexus.permission.persist.PolicyRepository.AssignmentRecord;
import one.nexus.permission.persist.PolicyRepository.RoleRecord;
import one.nexus.permission.support.PermissionException;

/**
 * PLA1: bind roles to a position (docs/planning/PL-平台级与租户级功能重新规划_V0.1.md §1.2). A person then holds the
 * roles of every ACTIVE employment's position — the permission engine already resolves {@code position} principals
 * ({@code IamSubjectDirectory} passes the position ids, {@code roleCodesFor} matches them).
 * <p>
 * Positions and roles are both tenant-scoped, so a platform position can only get platform roles and a tenant position
 * only that tenant's roles. Conflicting platform duties on one position are refused. Callers check who may do this
 * ({@code PermissionAdminGuard}); this service only keeps the binding consistent.
 */
public final class PositionRoleService {
    public static final String PRINCIPAL = "position";

    private final PermissionModule permissions;
    private final IdentityRepository identity;

    public PositionRoleService(PermissionModule permissions, IdentityRepository identity) {
        this.permissions = permissions;
        this.identity = identity;
    }

    /** A role bound to a position; {@code assignmentId} identifies the binding. */
    public record BoundRole(UUID assignmentId, UUID roleId, String roleCode, String roleName) {}

    /** All position bindings of the current tenant, keyed by position id (positions without roles are absent). */
    public Map<UUID, List<BoundRole>> all() {
        UUID tenant = tenant().tenantId();
        Map<UUID, List<BoundRole>> out = new LinkedHashMap<>();
        for (AssignmentRecord item : positionAssignments(tenant)) {
            UUID position = parse(item.principalId());
            if (position != null) {
                out.computeIfAbsent(position, key -> new ArrayList<>()).add(bound(tenant, item));
            }
        }
        out.values().forEach(list -> list.sort(Comparator.comparing(BoundRole::roleCode)));
        return out;
    }

    public List<BoundRole> rolesOf(UUID positionId) {
        UUID tenant = tenant().tenantId();
        requirePosition(tenant, positionId);
        return positionAssignments(tenant).stream()
                .filter(item -> positionId.toString().equals(item.principalId()))
                .map(item -> bound(tenant, item))
                .sorted(Comparator.comparing(BoundRole::roleCode))
                .toList();
    }

    /**
     * Makes the position hold exactly {@code roleIds}: missing bindings are added, extra ones removed. Everything is
     * validated before any change, so an invalid request changes nothing.
     */
    public List<BoundRole> set(UUID positionId, List<UUID> roleIds) {
        UUID tenant = tenant().tenantId();
        requirePosition(tenant, positionId);
        Set<UUID> wanted = new LinkedHashSet<>(roleIds == null ? List.of() : roleIds);
        List<RoleRecord> roles = new ArrayList<>();
        for (UUID roleId : wanted) {
            RoleRecord role = permissions.repository().findRole(tenant, roleId)
                    .orElseThrow(() -> PermissionException.notFound("role not found in current tenant"));
            if (role.status() != null && !"ACTIVE".equalsIgnoreCase(role.status())) {
                throw PermissionException.conflict("role is not active");
            }
            roles.add(role);
        }
        Set<String> codes = new LinkedHashSet<>();
        roles.forEach(role -> codes.add(role.code()));
        if (PlatformDutyCatalog.conflicting(codes)) {
            throw PermissionException.dutyConflict("a position cannot hold conflicting platform admin duties");
        }
        List<AssignmentRecord> current = positionAssignments(tenant).stream()
                .filter(item -> positionId.toString().equals(item.principalId()))
                .toList();
        Set<UUID> held = new LinkedHashSet<>();
        for (AssignmentRecord item : current) {
            if (!wanted.contains(item.roleId()) || !held.add(item.roleId())) {
                permissions.admin().unassign(item.id()); // not wanted any more, or a duplicate binding
            }
        }
        for (RoleRecord role : roles) {
            if (!held.contains(role.id())) {
                permissions.admin().assign(PRINCIPAL, positionId.toString(), role.id());
            }
        }
        return rolesOf(positionId);
    }

    private List<AssignmentRecord> positionAssignments(UUID tenant) {
        return permissions.repository().listAssignments(tenant).stream()
                .filter(item -> PRINCIPAL.equals(item.principalType()))
                .toList();
    }

    private BoundRole bound(UUID tenant, AssignmentRecord item) {
        RoleRecord role = permissions.repository().findRole(tenant, item.roleId()).orElse(null);
        return new BoundRole(item.id(), item.roleId(), role == null ? item.roleCode() : role.code(),
                role == null ? null : role.name());
    }

    private void requirePosition(UUID tenant, UUID positionId) {
        Model.Position position = positionId == null ? null : identity.findPosition(tenant, positionId).orElse(null);
        if (position == null || position.deletedAt() != null) {
            throw PermissionException.notFound("position not found in current tenant");
        }
    }

    private static TenantContext tenant() {
        TenantContext context = TenantContextHolder.get();
        if (context == null) {
            throw PermissionException.missingContext();
        }
        return context;
    }

    private static UUID parse(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException notUuid) {
            return null;
        }
    }
}
