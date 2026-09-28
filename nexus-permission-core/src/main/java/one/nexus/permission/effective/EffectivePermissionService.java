package one.nexus.permission.effective;

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
import one.nexus.permission.engine.PolicySnapshot;
import one.nexus.permission.engine.Rule;
import one.nexus.permission.persist.PolicyRepository.AssignmentRecord;
import one.nexus.permission.support.PermissionException;

/**
 * PLA3: effective-permission preview for a person or a position in the current tenant — which roles they end up with
 * and why (position binding, department, personal exception, inheritance), and the resulting grant per
 * resource/action. It follows the engine's evaluation: principals are the person, their ACTIVE employments' positions
 * and departments, and the (inherited) roles those hold; an explicit deny beats any grant; conflicting platform duties
 * deny everything. Data scope is shown as configured, not compiled against a record.
 */
public final class EffectivePermissionService {
    public static final String VIA_POSITION = "POSITION";
    public static final String VIA_DEPARTMENT = "DEPARTMENT";
    public static final String VIA_PERSONAL = "PERSONAL";
    public static final String VIA_INHERITED = "INHERITED";

    private final PermissionModule permissions;
    private final IdentityRepository identity;

    public EffectivePermissionService(PermissionModule permissions, IdentityRepository identity) {
        this.permissions = permissions;
        this.identity = identity;
    }

    /** Why a role is held: {@code via} one of the VIA_* constants, {@code viaId}/{@code viaLabel} name the source. */
    public record RoleSource(String roleCode, String roleName, String via, String viaId, String viaLabel) {}

    /** One matching rule: GRANT/DENY, its configured data scope, and who it is attached to. */
    public record RuleSource(String effect, String scope, String principalType, String principalId, String label) {}

    /** Final result for one resource/action: ALLOW or DENY, with every rule that contributed. */
    public record Permission(String resource, String action, String outcome, List<RuleSource> sources) {}

    public record Effective(String subjectType, UUID subjectId, List<RoleSource> roles, List<Permission> permissions,
            boolean dutyConflict, long policyVersion) {}

    public Effective forPerson(UUID personId) {
        UUID tenant = tenant();
        Model.Person person = personId == null ? null : identity.findPerson(tenant, personId).orElse(null);
        if (person == null || person.deletedAt() != null) {
            throw PermissionException.notFound("person not found in current tenant");
        }
        Set<String> positions = new LinkedHashSet<>();
        Set<String> departments = new LinkedHashSet<>();
        for (Model.Employment employment : identity.listEmploymentsByPerson(tenant, personId)) {
            if (employment.status() == Model.EmploymentStatus.ACTIVE && employment.deletedAt() == null) {
                positions.add(employment.positionId().toString());
                departments.add(employment.departmentId().toString());
            }
        }
        return evaluate(tenant, "PERSON", personId, personId.toString(), positions, departments);
    }

    /** What someone holding only this position gets from it (no personal or department grants). */
    public Effective forPosition(UUID positionId) {
        UUID tenant = tenant();
        Model.Position position = positionId == null ? null : identity.findPosition(tenant, positionId).orElse(null);
        if (position == null || position.deletedAt() != null) {
            throw PermissionException.notFound("position not found in current tenant");
        }
        return evaluate(tenant, "POSITION", positionId, null, Set.of(positionId.toString()), Set.of());
    }

    private Effective evaluate(UUID tenant, String subjectType, UUID subjectId, String userId,
            Set<String> positions, Set<String> departments) {
        PolicySnapshot snapshot = permissions.repository().loadSnapshot(tenant);
        Map<String, String> labels = new LinkedHashMap<>();
        if (userId != null) {
            labels.put("user:" + userId, "个人（例外授权）");
        }
        positions.forEach(id -> labels.put("position:" + id, positionLabel(tenant, id)));
        departments.forEach(id -> labels.put("department:" + id, departmentLabel(tenant, id)));

        // Directly held roles, with where each comes from.
        List<RoleSource> roles = new ArrayList<>();
        Set<String> held = new LinkedHashSet<>();
        for (AssignmentRecord item : permissions.repository().listAssignments(tenant)) {
            String via = switch (item.principalType()) {
                case "user" -> item.principalId().equals(userId) ? VIA_PERSONAL : null;
                case "position" -> positions.contains(item.principalId()) ? VIA_POSITION : null;
                case "department" -> departments.contains(item.principalId()) ? VIA_DEPARTMENT : null;
                default -> null;
            };
            if (via != null) {
                held.add(item.roleCode());
                roles.add(new RoleSource(item.roleCode(), roleName(tenant, item.roleCode()), via, item.principalId(),
                        labels.get(item.principalType() + ":" + item.principalId())));
            }
        }
        // Inherited roles, same walk as the engine.
        List<String> pending = new ArrayList<>(held);
        while (!pending.isEmpty()) {
            String child = pending.removeLast();
            for (String parent : snapshot.roleParents().getOrDefault(child, Set.of())) {
                if (held.add(parent)) {
                    pending.add(parent);
                    roles.add(new RoleSource(parent, roleName(tenant, parent), VIA_INHERITED, child, child));
                }
            }
        }
        held.forEach(code -> labels.put("role:" + code, code));

        boolean conflict = PlatformDutyCatalog.conflicting(held);
        Map<String, List<RuleSource>> byKey = new LinkedHashMap<>();
        Map<String, boolean[]> denied = new LinkedHashMap<>();
        collect(snapshot.grants(), "GRANT", labels, byKey, denied);
        collect(snapshot.denies(), "DENY", labels, byKey, denied);

        List<Permission> out = new ArrayList<>();
        for (Map.Entry<String, List<RuleSource>> entry : byKey.entrySet()) {
            String[] parts = entry.getKey().split("\u0000", 2);
            boolean deny = conflict || denied.get(entry.getKey())[0];
            boolean anyGrant = entry.getValue().stream().anyMatch(source -> "GRANT".equals(source.effect()));
            out.add(new Permission(parts[0], parts[1], !deny && anyGrant ? "ALLOW" : "DENY", List.copyOf(entry.getValue())));
        }
        out.sort(Comparator.comparing(Permission::resource).thenComparing(Permission::action));
        return new Effective(subjectType, subjectId, List.copyOf(roles), List.copyOf(out), conflict, snapshot.version());
    }

    private static void collect(List<Rule> rules, String effect, Map<String, String> labels,
            Map<String, List<RuleSource>> byKey, Map<String, boolean[]> denied) {
        for (Rule rule : rules) {
            String label = labels.get(rule.principalType() + ":" + rule.principalId());
            if (label == null) {
                continue; // not one of this subject's principals
            }
            String key = rule.resource() + "\u0000" + rule.action();
            byKey.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(new RuleSource(effect, String.valueOf(rule.scopeKind()), rule.principalType(), rule.principalId(), label));
            boolean[] flag = denied.computeIfAbsent(key, ignored -> new boolean[1]);
            if ("DENY".equals(effect)) {
                flag[0] = true;
            }
        }
    }

    private String positionLabel(UUID tenant, String id) {
        try {
            return identity.findPosition(tenant, UUID.fromString(id)).map(Model.Position::name).orElse(id);
        } catch (IllegalArgumentException notUuid) {
            return id;
        }
    }

    private String departmentLabel(UUID tenant, String id) {
        try {
            return identity.findDepartment(tenant, UUID.fromString(id)).map(Model.Department::name).orElse(id);
        } catch (IllegalArgumentException notUuid) {
            return id;
        }
    }

    private String roleName(UUID tenant, String code) {
        return permissions.repository().findRoleByCode(tenant, code).map(role -> role.name()).orElse(null);
    }

    private static UUID tenant() {
        TenantContext context = TenantContextHolder.get();
        if (context == null) {
            throw PermissionException.missingContext();
        }
        return context.tenantId();
    }
}
