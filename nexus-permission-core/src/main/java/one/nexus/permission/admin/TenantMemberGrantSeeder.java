package one.nexus.permission.admin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.engine.DataScopeKind;
import one.nexus.permission.persist.PolicyRepository;
import one.nexus.permission.persist.PolicyRepository.AssignmentRecord;
import one.nexus.permission.persist.PolicyRepository.GrantRecord;
import one.nexus.permission.persist.PolicyRepository.RoleRecord;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Demo-path bootstrap: role {@code tenant_member} with ledger/form read+write ALL,
 * process.task read/approve/reject, and process.instance start/withdraw, assigned to
 * the current person id
 * (M0-05 {@code IamSubjectDirectory} uses personId as userId; position
 * {@code TENANT_ADMIN} codes do not match).
 *
 * <p>Product default is {@code nexus.permission.seed-tenant-member-grants=true}.
 * Permission-core ITs leave the property unset so they still observe
 * {@code no_grant} until an explicit grant is written.
 *
 * <p>Must run after a server-issued TenantContext is bound — including the
 * browser permission shell ({@code /api/v1/permission/snapshot|check}), not
 * only P3 ledger hooks. Call {@link #ensure()} from those filters.
 *
 * <p>This seed is <strong>not</strong> G2-complete: every cell is
 * {@code DataScopeKind.ALL} with an empty field map. Use
 * {@link RoleMatrixCatalog} / {@code POST /api/v1/permission/matrix/example}
 * for SELF / DEPT_AND_CHILDREN + field policy.
 */
public class TenantMemberGrantSeeder {
    public static final String ROLE_CODE = "tenant_member";

    private static final List<String[]> GRANTS = List.of(
            new String[] {"ledger", "read"},
            new String[] {"ledger", "write"},
            new String[] {"form", "read"},
            new String[] {"form", "write"},
            new String[] {"process.task", "read"},
            new String[] {"process.task", "approve"},
            new String[] {"process.task", "reject"},
            new String[] {"process.instance", "start"},
            new String[] {"process.instance", "withdraw"});

    private final PermissionModule permission;
    private final ConcurrentHashMap<UUID, Object> locks = new ConcurrentHashMap<>();

    public TenantMemberGrantSeeder(PermissionModule permission) {
        this.permission = permission;
    }

    /**
     * No-op when the seeder bean is off (permission-core ITs) or the provider
     * is missing. Safe to call from every TenantContext binder.
     */
    public static void ensureIfPresent(ObjectProvider<TenantMemberGrantSeeder> grantSeeder) {
        if (grantSeeder == null) {
            return;
        }
        TenantMemberGrantSeeder seeder = grantSeeder.getIfAvailable();
        if (seeder != null) {
            seeder.ensure();
        }
    }

    public void ensure() {
        TenantContext context = TenantContextHolder.get();
        if (context == null || context.personId() == null) {
            return;
        }
        Object lock = locks.computeIfAbsent(context.tenantId(), ignored -> new Object());
        synchronized (lock) {
            seed(context);
        }
    }

    private void seed(TenantContext context) {
        PolicyRepository repository = permission.repository();
        PolicyAdminService admin = permission.admin();
        UUID tenantId = context.tenantId();
        RoleRecord role = repository.findRoleByCode(tenantId, ROLE_CODE).orElseGet(
                () -> admin.createRole(ROLE_CODE, "Tenant member", true));
        String personId = context.personId().toString();
        boolean assigned = repository.listAssignments(tenantId).stream()
                .anyMatch(item -> matchesAssignment(item, personId, role.id()));
        if (!assigned) {
            admin.assign("user", personId, role.id());
        }
        for (String[] pair : GRANTS) {
            String resource = pair[0];
            String action = pair[1];
            boolean present = repository.listGrants(tenantId).stream()
                    .anyMatch(grant -> matchesGrant(grant, resource, action));
            if (!present) {
                admin.grant(
                        "GRANT",
                        "role",
                        ROLE_CODE,
                        resource,
                        action,
                        DataScopeKind.ALL,
                        List.of(),
                        Map.of());
            }
        }
    }

    private static boolean matchesAssignment(AssignmentRecord item, String personId, UUID roleId) {
        return "user".equals(item.principalType())
                && personId.equals(item.principalId())
                && roleId.equals(item.roleId());
    }

    private static boolean matchesGrant(GrantRecord grant, String resource, String action) {
        if (!"GRANT".equalsIgnoreCase(grant.effect()) || grant.rule() == null) {
            return false;
        }
        return "role".equals(grant.rule().principalType())
                && ROLE_CODE.equals(grant.rule().principalId())
                && resource.equals(grant.rule().resource())
                && action.equals(grant.rule().action());
    }
}
