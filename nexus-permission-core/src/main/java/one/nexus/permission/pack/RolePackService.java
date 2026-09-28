package one.nexus.permission.pack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.AuditPort;
import one.nexus.common.id.UuidV7;
import one.nexus.common.trace.TraceIds;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.engine.DataScopeKind;
import one.nexus.permission.engine.Rule;
import one.nexus.permission.persist.PolicyRepository;
import one.nexus.permission.persist.PolicyRepository.GrantRecord;
import one.nexus.permission.persist.PolicyRepository.RoleRecord;
import one.nexus.permission.redis.PolicyInvalidationPort;
import one.nexus.permission.support.PermissionException;

/**
 * PLA7 / PLX1 C3: lands an application's role pack in a tenant — creates the {@code app_{app}_{role}} roles and their
 * default grants. Idempotent: an existing pack role only gets its missing grants; grants a newer version dropped are
 * reported in {@code grantsNotInPack}, never revoked. Roles are <b>not</b> bound to positions (D4) — the tenant admin
 * does that on the position page, so a landed but unbound role affects nobody.
 * <p>
 * A pack may only grant {@code read}/{@code write} on its own application's entries ({@code fn.{app}.{form}}); anything
 * else is refused before any write, so a manifest cannot smuggle in admin capabilities.
 */
public final class RolePackService {
    public static final String ROLE_PREFIX = "app_";
    private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9_]{0,23}");
    private static final Pattern VERSION = Pattern.compile("\\d+\\.\\d+\\.\\d+");
    private static final Set<String> ACTIONS = Set.of("read", "write");
    private static final Set<DataScopeKind> SCOPES =
            Set.of(DataScopeKind.SELF, DataScopeKind.DEPT, DataScopeKind.DEPT_AND_CHILDREN, DataScopeKind.ALL);

    private final PolicyRepository repository;
    private final PolicyInvalidationPort invalidation;
    private final AuditPort audits;

    public RolePackService(PolicyRepository repository, PolicyInvalidationPort invalidation, AuditPort audits) {
        this.repository = repository;
        this.invalidation = invalidation;
        this.audits = audits;
    }

    public record RolePackCommand(String appCode, String appVersion, List<RoleSpec> rolePack) {}

    public record RoleSpec(String code, String name, List<GrantSpec> grants) {}

    public record GrantSpec(String resource, String action, String scope) {}

    public record RolePackResult(List<AppliedRole> roles) {}

    public record AppliedRole(String tenantRoleCode, boolean created, int grantsAdded, List<String> grantsNotInPack) {}

    /** Tenant-side subscribe / upgrade: the tenant is the bound context; callers never pass one. */
    public RolePackResult apply(RolePackCommand command) {
        TenantContext context = TenantContextHolder.get();
        if (context == null) {
            throw PermissionException.missingContext();
        }
        return land(context.tenantId(), context.personId(), false, TraceIds.currentOrUnknown(), command);
    }

    /**
     * Platform-approved activation: the target tenant comes only from {@code target}, which must still be inside its
     * issuing request. No tenant context is impersonated; the audit names the platform operator as actor.
     */
    public RolePackResult applyApproved(ApprovedTenantWrite target, RolePackCommand command) {
        Objects.requireNonNull(target, "target");
        target.assertActive();
        UUID tenant = Objects.requireNonNull(target.targetTenantId(), "targetTenantId");
        return land(tenant, target.actorId(), true, target.traceId(), command);
    }

    /** {@code app_{app}_{role}}; both parts ≤ 24, so the code stays within {@code iam.perm_role.code} (64). */
    public static String tenantRoleCode(String appCode, String roleCode) {
        return ROLE_PREFIX + appCode + "_" + roleCode;
    }

    private RolePackResult land(UUID tenant, UUID actor, boolean approved, String traceId, RolePackCommand command) {
        List<Planned> plan = validate(command);
        List<AppliedRole> applied = new ArrayList<>();
        boolean changed = false;
        List<GrantRecord> existingGrants = repository.listGrants(tenant);
        for (Planned role : plan) {
            RoleRecord current = repository.findRoleByCode(tenant, role.tenantCode()).orElse(null);
            boolean created = false;
            if (current == null) {
                repository.saveRole(new RoleRecord(UuidV7.generate(), tenant, role.tenantCode(), role.name(), true, "ACTIVE"));
                created = true;
                changed = true;
            } else if (!current.systemRole()) {
                throw PermissionException.conflict("a tenant-created role already uses this application role code");
            }
            Set<String> held = new HashSet<>();
            for (GrantRecord grant : existingGrants) {
                if (isRoleGrant(grant, role.tenantCode())) {
                    held.add(key(grant.rule().resource(), grant.rule().action(), grant.rule().scopeKind()));
                }
            }
            int added = 0;
            Set<String> wanted = new LinkedHashSet<>();
            for (GrantSpec grant : role.grants()) {
                DataScopeKind scope = DataScopeKind.valueOf(grant.scope());
                String key = key(grant.resource(), grant.action(), scope);
                wanted.add(key);
                if (held.add(key)) {
                    repository.saveGrant(new GrantRecord(UuidV7.generate(), tenant, "GRANT",
                            Rule.scoped("role", role.tenantCode(), grant.resource(), grant.action(), scope, List.of(), Map.of())));
                    added++;
                    changed = true;
                }
            }
            List<String> notInPack = held.stream().filter(key -> !wanted.contains(key)).sorted().toList();
            applied.add(new AppliedRole(role.tenantCode(), created, added, notInPack));
        }
        if (changed) {
            repository.bumpVersion(tenant);
            if (invalidation != null) {
                invalidation.notifyTenant(tenant.toString());
            }
        }
        audit(tenant, actor, approved, traceId, command, applied);
        return new RolePackResult(List.copyOf(applied));
    }

    private record Planned(String tenantCode, String name, List<GrantSpec> grants) {}

    /** Everything is checked before the first write, so an invalid pack changes nothing. */
    private static List<Planned> validate(RolePackCommand command) {
        if (command == null || command.appCode() == null || !CODE.matcher(command.appCode()).matches()) {
            throw PermissionException.badRequest("app_code", "app_code must match ^[a-z][a-z0-9_]{0,23}$");
        }
        if (command.appVersion() == null || !VERSION.matcher(command.appVersion()).matches()) {
            throw PermissionException.badRequest("app_version", "app_version must be MAJOR.MINOR.PATCH");
        }
        String own = "fn." + command.appCode() + ".";
        List<Planned> plan = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (RoleSpec role : command.rolePack() == null ? List.<RoleSpec>of() : command.rolePack()) {
            if (role == null || role.code() == null || !CODE.matcher(role.code()).matches()) {
                throw PermissionException.badRequest("role_pack", "role code must match ^[a-z][a-z0-9_]{0,23}$");
            }
            if (!codes.add(role.code())) {
                throw PermissionException.badRequest("role_pack", "duplicate role code in pack: " + role.code());
            }
            List<GrantSpec> grants = role.grants() == null ? List.of() : role.grants();
            for (GrantSpec grant : grants) {
                String form = grant == null || grant.resource() == null || !grant.resource().startsWith(own)
                        ? null : grant.resource().substring(own.length());
                if (form == null || !CODE.matcher(form).matches()) {
                    throw PermissionException.badRequest("role_pack", "a pack may only grant its own entries (" + own + "{form})");
                }
                if (!ACTIONS.contains(grant.action())) {
                    throw PermissionException.badRequest("role_pack", "grant action must be read or write");
                }
                if (!validScope(grant.scope())) {
                    throw PermissionException.badRequest("role_pack", "grant scope must be SELF, DEPT, DEPT_AND_CHILDREN or ALL");
                }
            }
            String name = role.name() == null || role.name().isBlank() ? role.code() : role.name();
            plan.add(new Planned(tenantRoleCode(command.appCode(), role.code()), name, grants));
        }
        return plan;
    }

    private static boolean validScope(String scope) {
        if (scope == null) {
            return false;
        }
        try {
            return SCOPES.contains(DataScopeKind.valueOf(scope));
        } catch (IllegalArgumentException unknown) {
            return false;
        }
    }

    private static boolean isRoleGrant(GrantRecord grant, String roleCode) {
        return grant.rule() != null && "GRANT".equalsIgnoreCase(grant.effect())
                && "role".equals(grant.rule().principalType()) && roleCode.equals(grant.rule().principalId());
    }

    private static String key(String resource, String action, DataScopeKind scope) {
        return resource + ":" + action + ":" + scope;
    }

    private void audit(UUID tenant, UUID actor, boolean approved, String traceId, RolePackCommand command,
            List<AppliedRole> applied) {
        if (audits == null) {
            return;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("app_code", command.appCode());
        details.put("app_version", command.appVersion());
        details.put("roles", applied.stream().map(AppliedRole::tenantRoleCode).toList().toString());
        details.put("created", applied.stream().filter(AppliedRole::created).count());
        details.put("grants_added", applied.stream().mapToInt(AppliedRole::grantsAdded).sum());
        details.put("path", approved ? "platform_approved" : "tenant");
        audits.append(AuditCaptures.permission(true, tenant, actor, "role_pack", command.appCode(), "applied",
                traceId == null ? TraceIds.currentOrUnknown() : traceId, details));
    }
}
