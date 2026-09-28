package one.nexus.permission.api;

import java.util.List;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.TenantAdminPositions;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.support.PermissionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * PLA0: who may read or change the permission policy through {@code /api/v1/permission}.
 * <ul>
 *   <li>changes need the matching grant: {@code permission.role:write} (roles, example / duty installs),
 *       {@code permission.grant:write} (grants), {@code permission.assignment:write} (assignments) — the platform
 *       security-admin duty holds all three;</li>
 *   <li>reading roles / assignments / grants / matrix / duties needs {@code permission.role:read} or any of the
 *       write grants above;</li>
 *   <li>bootstrap: a person in a tenant-admin position ({@link TenantAdminPositions}) of the current tenant may
 *       manage that tenant's policy even before any grant exists, so the first admin is never locked out.</li>
 * </ul>
 * Everything else is refused with {@code no_grant}. Self checks ({@code /check}) and the snapshot counters stay open
 * to every signed-in member.
 */
@Component
public class PermissionAdminGuard {
    public static final String ROLE = "permission.role";
    public static final String GRANT = "permission.grant";
    public static final String ASSIGNMENT = "permission.assignment";

    private final PermissionModule permissions;
    private final ObjectProvider<IdentityModule> identity;

    public PermissionAdminGuard(PermissionModule permissions, ObjectProvider<IdentityModule> identity) {
        this.permissions = permissions;
        this.identity = identity;
    }

    public void requireWrite(String resource) {
        TenantContext context = requireContext();
        if (allowed(resource, "write") || tenantAdmin(context)) {
            return;
        }
        throw PermissionException.denied("no_grant");
    }

    public void requireRead() {
        TenantContext context = requireContext();
        for (var pair : List.of(new String[] {ROLE, "read"}, new String[] {ROLE, "write"},
                new String[] {GRANT, "write"}, new String[] {ASSIGNMENT, "write"})) {
            if (allowed(pair[0], pair[1])) {
                return;
            }
        }
        if (tenantAdmin(context)) {
            return;
        }
        throw PermissionException.denied("no_grant");
    }

    private boolean allowed(String resource, String action) {
        try {
            Decision decision = permissions.service().decide(resource, action);
            return decision.allowed();
        } catch (PermissionException refused) {
            return false;
        }
    }

    private boolean tenantAdmin(TenantContext context) {
        IdentityModule module = identity.getIfAvailable();
        return module != null && TenantAdminPositions.holds(module.repository(), context);
    }

    private static TenantContext requireContext() {
        TenantContext context = TenantContextHolder.get();
        if (context == null || context.personId() == null) {
            throw PermissionException.missingContext();
        }
        return context;
    }
}
