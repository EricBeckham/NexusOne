package one.nexus.bootstrap.demo;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.engine.DataScopeKind;
import one.nexus.permission.persist.PolicyRepository;
import one.nexus.permission.surface.SurfaceCatalog;

/**
 * DEMO-U5: on demo_acme, 林晓 holds {@code tenant_admin} (report read, export, manage)
 * and 周然 holds only {@code tenant_member}. Member export and manage stay {@code no_grant}.
 */
public final class DemoAcmeRoleSplit {
    public static final String TENANT_ADMIN = "tenant_admin";
    public static final String MANAGE_RESOURCE = "tenant.admin";
    public static final String MANAGE_ACTION = "manage";

    private DemoAcmeRoleSplit() {}

    public static void install(PermissionModule permissions) {
        install(permissions, new TenantMemberGrantSeeder(permissions));
    }

    public static void install(PermissionModule permissions, TenantMemberGrantSeeder members) {
        UUID tenant = P1DemoSeedConfiguration.TENANT_ID;
        TenantContext previous = TenantContextHolder.get();
        try {
            bind(tenant, P2DemoSeedConfiguration.PERSON_LIN);
            PolicyRepository.RoleRecord admin = permissions.repository().findRoleByCode(tenant, TENANT_ADMIN)
                    .orElseGet(() -> permissions.admin().createRole(TENANT_ADMIN, "租户管理", true));
            ensureGrant(permissions, tenant, "ledger", "read");
            ensureGrant(permissions, tenant, SurfaceCatalog.EXPORT_RESOURCE, SurfaceCatalog.EXPORT_ACTION);
            ensureGrant(permissions, tenant, MANAGE_RESOURCE, MANAGE_ACTION);
            assign(permissions, tenant, P2DemoSeedConfiguration.PERSON_LIN, admin);

            bind(tenant, P2DemoSeedConfiguration.PERSON_ZHOU);
            members.ensure();
        } finally {
            if (previous == null) {
                TenantContextHolder.clear();
            } else {
                TenantContextHolder.set(previous);
            }
        }
    }

    private static void ensureGrant(PermissionModule permissions, UUID tenant, String resource, String action) {
        boolean present = permissions.repository().listGrants(tenant).stream().anyMatch(grant ->
                grant.rule() != null
                        && "GRANT".equalsIgnoreCase(grant.effect())
                        && "role".equals(grant.rule().principalType())
                        && TENANT_ADMIN.equals(grant.rule().principalId())
                        && resource.equals(grant.rule().resource())
                        && action.equals(grant.rule().action()));
        if (!present) {
            permissions.admin().grant(
                    "GRANT", "role", TENANT_ADMIN, resource, action, DataScopeKind.ALL, List.of(), Map.of());
        }
    }

    private static void assign(PermissionModule permissions, UUID tenant, UUID personId, PolicyRepository.RoleRecord role) {
        String principal = personId.toString();
        boolean assigned = permissions.repository().listAssignments(tenant).stream().anyMatch(item ->
                "user".equals(item.principalType())
                        && principal.equals(item.principalId())
                        && role.id().equals(item.roleId()));
        if (!assigned) {
            permissions.admin().assign("user", principal, role.id());
        }
    }

    private static void bind(UUID tenant, UUID personId) {
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenant, null, personId, "demo-acme-role-split",
                Instant.now(), Instant.now().plusSeconds(60)));
    }
}
