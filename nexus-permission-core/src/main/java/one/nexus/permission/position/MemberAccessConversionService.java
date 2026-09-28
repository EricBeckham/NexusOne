package one.nexus.permission.position;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.domain.Model;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.MemberAccessMode;
import one.nexus.permission.admin.TenantAdminPositions;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.persist.PolicyRepository.AssignmentRecord;
import one.nexus.permission.persist.PolicyRepository.RoleRecord;
import one.nexus.permission.support.PermissionException;

/**
 * PLA5: moves the current tenant from per-person {@code tenant_member} seeding to position bindings.
 * <ol>
 *   <li>binds {@code tenant_member} to every position that has at least one ACTIVE employment;</li>
 *   <li>binds the process-designer role to the tenant-admin positions among them;</li>
 *   <li>only in {@link MemberAccessMode#POSITION}: removes the per-person seed assignments of those two roles
 *       (in LEGACY the seeder would simply re-add them, so they are left alone).</li>
 * </ol>
 * Additive and idempotent; a dry run reports the same plan without writing. {@code personsWithoutPosition} counts the
 * ACTIVE people who hold no ACTIVE employment — in POSITION mode they lose the member baseline until they get one.
 */
public final class MemberAccessConversionService {
    private final PermissionModule permissions;
    private final IdentityRepository identity;
    private final TenantMemberGrantSeeder seeder;
    private final MemberAccessMode mode;

    public MemberAccessConversionService(PermissionModule permissions, IdentityRepository identity,
            TenantMemberGrantSeeder seeder, MemberAccessMode mode) {
        this.permissions = permissions;
        this.identity = identity;
        this.seeder = seeder == null ? new TenantMemberGrantSeeder(permissions, identity, mode) : seeder;
        this.mode = mode == null ? MemberAccessMode.LEGACY : mode;
    }

    public record Result(
            String mode,
            boolean dryRun,
            List<String> memberPositions,
            List<String> designerPositions,
            int bindingsAdded,
            int seedAssignmentsRemoved,
            int personsWithoutPosition) {}

    public Result convert(boolean dryRun) {
        TenantContext context = TenantContextHolder.get();
        if (context == null) {
            throw PermissionException.missingContext();
        }
        UUID tenant = context.tenantId();
        if (!dryRun) {
            seeder.ensureRoleDefinitions();
        }
        RoleRecord member = permissions.repository().findRoleByCode(tenant, TenantMemberGrantSeeder.ROLE_CODE).orElse(null);
        RoleRecord designer = permissions.repository().findRoleByCode(tenant, TenantMemberGrantSeeder.DESIGNER_ROLE).orElse(null);

        // Which positions actually have people, and who has none.
        Map<UUID, Model.Position> occupied = new HashMap<>();
        int withoutPosition = 0;
        for (Model.Person person : identity.listPersons(tenant)) {
            if (person.status() != Model.PersonStatus.ACTIVE || person.deletedAt() != null) {
                continue;
            }
            boolean hasOne = false;
            for (Model.Employment employment : identity.listEmploymentsByPerson(tenant, person.id())) {
                if (employment.status() != Model.EmploymentStatus.ACTIVE || employment.deletedAt() != null) {
                    continue;
                }
                Model.Position position = identity.findPosition(tenant, employment.positionId()).orElse(null);
                if (position != null && position.deletedAt() == null) {
                    occupied.put(position.id(), position);
                    hasOne = true;
                }
            }
            if (!hasOne) {
                withoutPosition++;
            }
        }

        List<AssignmentRecord> assignments = permissions.repository().listAssignments(tenant);
        List<String> memberPositions = new ArrayList<>();
        List<String> designerPositions = new ArrayList<>();
        int added = 0;
        for (Model.Position position : occupied.values().stream()
                .sorted((a, b) -> a.code().compareTo(b.code())).toList()) {
            memberPositions.add(position.code());
            added += bind(assignments, position, member, dryRun);
            if (TenantAdminPositions.isAdminCode(position.code())) {
                designerPositions.add(position.code());
                added += bind(assignments, position, designer, dryRun);
            }
        }

        int removed = 0;
        if (mode == MemberAccessMode.POSITION) {
            Set<UUID> seedRoles = new HashSet<>();
            if (member != null) seedRoles.add(member.id());
            if (designer != null) seedRoles.add(designer.id());
            for (AssignmentRecord item : assignments) {
                if ("user".equals(item.principalType()) && seedRoles.contains(item.roleId())) {
                    if (!dryRun) {
                        permissions.admin().unassign(item.id());
                    }
                    removed++;
                }
            }
        }
        return new Result(mode.name(), dryRun, List.copyOf(memberPositions), List.copyOf(designerPositions),
                added, removed, withoutPosition);
    }

    /** 1 when a binding is (or, on a dry run, would be) added; 0 when the position already holds the role. */
    private int bind(List<AssignmentRecord> assignments, Model.Position position, RoleRecord role, boolean dryRun) {
        String principal = position.id().toString();
        boolean held = role != null && assignments.stream().anyMatch(item -> PositionRoleService.PRINCIPAL.equals(item.principalType())
                && principal.equals(item.principalId()) && role.id().equals(item.roleId()));
        if (held) {
            return 0;
        }
        if (!dryRun && role != null) {
            permissions.admin().assign(PositionRoleService.PRINCIPAL, principal, role.id());
        }
        return 1;
    }
}
