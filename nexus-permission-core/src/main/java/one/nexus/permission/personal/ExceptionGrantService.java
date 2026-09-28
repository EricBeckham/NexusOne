package one.nexus.permission.personal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.domain.Model;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.persist.PolicyRepository.AssignmentRecord;
import one.nexus.permission.persist.PolicyRepository.GrantRecord;
import one.nexus.permission.support.PermissionException;

/**
 * PLA4 (D5): exception grants — everything attached to a single person ({@code user} principal) instead of reaching
 * them through a position. Lists them with the person's name, filters them, and revokes them. Audit is deferred (PL0.2).
 * <p>
 * Some personal assignments are made by the system, not by an admin: the legacy {@code tenant_member} seed, the
 * process-designer seed and product permission packs ({@code product_*}). They are flagged {@code system} and hidden
 * unless asked for, and cannot be revoked here (the seeder would just re-add them).
 */
public final class ExceptionGrantService {
    public static final String KIND_GRANT = "GRANT";
    public static final String KIND_ROLE = "ROLE";
    static final String USER = "user";
    static final Set<String> SEED_ROLES = Set.of(TenantMemberGrantSeeder.ROLE_CODE, TenantMemberGrantSeeder.DESIGNER_ROLE);
    static final String PRODUCT_PACK_PREFIX = "product_";

    private final PermissionModule permissions;
    private final IdentityRepository identity;

    public ExceptionGrantService(PermissionModule permissions, IdentityRepository identity) {
        this.permissions = permissions;
        this.identity = identity;
    }

    /**
     * One exception. For {@code GRANT}: {@code effect} GRANT/DENY on {@code resource}:{@code action} with {@code scope}.
     * For {@code ROLE}: the personally assigned {@code roleCode}. {@code id} is the grant or assignment id.
     */
    public record ExceptionItem(String kind, UUID id, UUID personId, String personName, String effect,
            String resource, String action, String scope, String roleCode, String roleName, boolean system) {}

    /** All filters optional. {@code query} matches resource, action, role code/name or person name (case-insensitive). */
    public record Filter(UUID personId, String kind, String query, boolean includeSystem) {}

    public List<ExceptionItem> list(Filter filter) {
        UUID tenant = tenant();
        Filter f = filter == null ? new Filter(null, null, null, false) : filter;
        List<ExceptionItem> out = new ArrayList<>();
        if (f.kind() == null || KIND_GRANT.equalsIgnoreCase(f.kind())) {
            for (GrantRecord grant : permissions.repository().listGrants(tenant)) {
                if (grant.rule() == null || !USER.equals(grant.rule().principalType())) {
                    continue;
                }
                UUID person = parse(grant.rule().principalId());
                out.add(new ExceptionItem(KIND_GRANT, grant.id(), person, personName(tenant, person),
                        String.valueOf(grant.effect()).toUpperCase(Locale.ROOT), grant.rule().resource(),
                        grant.rule().action(), String.valueOf(grant.rule().scopeKind()), null, null, false));
            }
        }
        if (f.kind() == null || KIND_ROLE.equalsIgnoreCase(f.kind())) {
            for (AssignmentRecord item : permissions.repository().listAssignments(tenant)) {
                if (!USER.equals(item.principalType())) {
                    continue;
                }
                UUID person = parse(item.principalId());
                String roleName = permissions.repository().findRole(tenant, item.roleId()).map(role -> role.name()).orElse(null);
                out.add(new ExceptionItem(KIND_ROLE, item.id(), person, personName(tenant, person), null, null, null, null,
                        item.roleCode(), roleName, isSystemRole(item.roleCode())));
            }
        }
        String q = f.query() == null || f.query().isBlank() ? null : f.query().trim().toLowerCase(Locale.ROOT);
        return out.stream()
                .filter(item -> f.includeSystem() || !item.system())
                .filter(item -> f.personId() == null || f.personId().equals(item.personId()))
                .filter(item -> q == null || matches(item, q))
                .sorted(Comparator.comparing((ExceptionItem item) -> String.valueOf(item.personName()))
                        .thenComparing(ExceptionItem::kind)
                        .thenComparing(item -> String.valueOf(item.resource() == null ? item.roleCode() : item.resource())))
                .toList();
    }

    /** Revokes a personal grant. False when it does not exist; refused when it is not a personal grant. */
    public boolean revokeGrant(UUID grantId) {
        UUID tenant = tenant();
        GrantRecord grant = permissions.repository().listGrants(tenant).stream()
                .filter(item -> item.id().equals(grantId)).findFirst().orElse(null);
        if (grant == null) {
            return false;
        }
        if (grant.rule() == null || !USER.equals(grant.rule().principalType())) {
            throw PermissionException.conflict("only personal (exception) grants can be revoked here");
        }
        return permissions.admin().revokeGrant(grantId);
    }

    /** Removes a personal role assignment. System-made ones (seeds, product packs) are refused. */
    public boolean revokeAssignment(UUID assignmentId) {
        UUID tenant = tenant();
        AssignmentRecord item = permissions.repository().listAssignments(tenant).stream()
                .filter(a -> a.id().equals(assignmentId)).findFirst().orElse(null);
        if (item == null) {
            return false;
        }
        if (!USER.equals(item.principalType())) {
            throw PermissionException.conflict("only personal (exception) role assignments can be revoked here");
        }
        if (isSystemRole(item.roleCode())) {
            throw PermissionException.conflict("system-made assignments are managed by the system, not here");
        }
        return permissions.admin().unassign(assignmentId);
    }

    static boolean isSystemRole(String roleCode) {
        return roleCode != null && (SEED_ROLES.contains(roleCode) || roleCode.startsWith(PRODUCT_PACK_PREFIX));
    }

    private static boolean matches(ExceptionItem item, String q) {
        for (String value : new String[] {item.resource(), item.action(), item.roleCode(), item.roleName(), item.personName()}) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(q)) {
                return true;
            }
        }
        return false;
    }

    private String personName(UUID tenant, UUID person) {
        return person == null ? null
                : identity.findPerson(tenant, person).map(Model.Person::displayName).orElse(null);
    }

    private static UUID parse(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException notUuid) {
            return null;
        }
    }

    private static UUID tenant() {
        TenantContext context = TenantContextHolder.get();
        if (context == null) {
            throw PermissionException.missingContext();
        }
        return context.tenantId();
    }
}
