package one.nexus.permission.admin;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import one.nexus.permission.engine.DataScopeKind;

/**
 * ARCH-CMP-002 / P1-M05-G01-F003 three-power split. Catalog only — installing
 * does not assign roles to the current person. The three platform admin roles
 * are mutually exclusive in one tenant / platform scope.
 *
 * <p>This is a G17 <strong>slice</strong> (separation of duties). It does not
 * close classified / GM TLS / MFA / password-policy G17.
 */
public final class PlatformDutyCatalog {
    public static final String SYSTEM_ADMIN = "platform_system_admin";
    public static final String SECURITY_ADMIN = "platform_security_admin";
    public static final String AUDIT_ADMIN = "platform_audit_admin";

    public static final String RESOURCE_AUDIT = "audit";
    public static final String RESOURCE_AUDIT_CONFIG = "audit.config";
    public static final String RESOURCE_AUDIT_CHAIN = "audit.chain";
    public static final String ACTION_READ = "read";
    public static final String ACTION_WRITE = "write";
    public static final String ACTION_DELETE = "delete";
    public static final String ACTION_ADMIN = "admin";
    public static final String ACTION_MUTE = "mute";
    public static final String ACTION_VERIFY = "verify";

    public static final Set<String> DUTY_ROLES = Set.of(SYSTEM_ADMIN, SECURITY_ADMIN, AUDIT_ADMIN);

    public static final String COMPLETENESS_NOTE =
            "platform_system_admin / platform_security_admin / platform_audit_admin are mutually "
                    + "exclusive in one tenant. Audit admin may read traces and verify the hash chain; "
                    + "audit.write / delete / admin / config / mute are DENY. Not a full G17 close "
                    + "(no GM TLS, forced MFA, or classified delivery). tenant_member remains demo "
                    + "bootstrap and is not a platform duty.";

    private PlatformDutyCatalog() {}

    public record RoleSpec(String code, String name, String duty) {}

    public record CellSpec(
            String roleCode,
            String effect,
            String resource,
            String action,
            DataScopeKind scopeKind
    ) {}

    public static List<RoleSpec> roles() {
        return List.of(
                new RoleSpec(SYSTEM_ADMIN, "系统管理员", "SYSTEM_ADMIN"),
                new RoleSpec(SECURITY_ADMIN, "安全管理员", "SECURITY_ADMIN"),
                new RoleSpec(AUDIT_ADMIN, "审计管理员", "AUDIT_ADMIN"));
    }

    /**
     * Grant sets are disjoint across the three duties. Audit-admin has explicit
     * DENY on every mutation of audit config or the hash chain.
     */
    public static List<CellSpec> cells() {
        return List.of(
                grant(SYSTEM_ADMIN, "tenant", "create"),
                grant(SYSTEM_ADMIN, "tenant", "enable"),
                grant(SYSTEM_ADMIN, "tenant", "disable"),
                grant(SYSTEM_ADMIN, "tenant", "list"),
                grant(SYSTEM_ADMIN, "tenant", "get"),
                grant(SYSTEM_ADMIN, "tenant.config", "get"),
                grant(SYSTEM_ADMIN, "tenant.config", "update"),
                grant(SYSTEM_ADMIN, "tenant.entitlement", "get"),
                grant(SYSTEM_ADMIN, "quota", "consume"),
                grant(SYSTEM_ADMIN, "quota", "release"),
                deny(SYSTEM_ADMIN, RESOURCE_AUDIT, ACTION_WRITE),
                deny(SYSTEM_ADMIN, RESOURCE_AUDIT, ACTION_DELETE),
                deny(SYSTEM_ADMIN, RESOURCE_AUDIT, ACTION_ADMIN),

                grant(SECURITY_ADMIN, "permission.role", ACTION_WRITE),
                grant(SECURITY_ADMIN, "permission.grant", ACTION_WRITE),
                grant(SECURITY_ADMIN, "permission.assignment", ACTION_WRITE),
                grant(SECURITY_ADMIN, "security.policy", ACTION_WRITE),
                grant(SECURITY_ADMIN, "security.mfa", ACTION_WRITE),
                deny(SECURITY_ADMIN, RESOURCE_AUDIT, ACTION_WRITE),
                deny(SECURITY_ADMIN, RESOURCE_AUDIT, ACTION_DELETE),
                deny(SECURITY_ADMIN, RESOURCE_AUDIT, ACTION_ADMIN),

                grant(AUDIT_ADMIN, RESOURCE_AUDIT, ACTION_READ),
                grant(AUDIT_ADMIN, RESOURCE_AUDIT, ACTION_VERIFY),
                deny(AUDIT_ADMIN, RESOURCE_AUDIT, ACTION_WRITE),
                deny(AUDIT_ADMIN, RESOURCE_AUDIT, ACTION_DELETE),
                deny(AUDIT_ADMIN, RESOURCE_AUDIT, ACTION_ADMIN),
                deny(AUDIT_ADMIN, RESOURCE_AUDIT_CONFIG, ACTION_WRITE),
                deny(AUDIT_ADMIN, RESOURCE_AUDIT_CHAIN, ACTION_MUTE),
                deny(AUDIT_ADMIN, RESOURCE_AUDIT_CHAIN, ACTION_DELETE));
    }

    public static boolean isDutyRole(String roleCode) {
        return roleCode != null && DUTY_ROLES.contains(roleCode);
    }

    public static Set<String> heldDuties(Set<String> roleCodes) {
        Set<String> held = new LinkedHashSet<>();
        if (roleCodes == null) {
            return Set.of();
        }
        for (String code : roleCodes) {
            if (isDutyRole(code)) {
                held.add(code);
            }
        }
        return Set.copyOf(held);
    }

    public static boolean conflicting(Set<String> roleCodes) {
        return heldDuties(roleCodes).size() >= 2;
    }

    public static List<List<String>> exclusivePairs() {
        return List.of(
                List.of(SYSTEM_ADMIN, SECURITY_ADMIN),
                List.of(SYSTEM_ADMIN, AUDIT_ADMIN),
                List.of(SECURITY_ADMIN, AUDIT_ADMIN));
    }

    private static CellSpec grant(String role, String resource, String action) {
        return new CellSpec(role, "GRANT", resource, action, DataScopeKind.ALL);
    }

    private static CellSpec deny(String role, String resource, String action) {
        return new CellSpec(role, "DENY", resource, action, DataScopeKind.ALL);
    }
}
