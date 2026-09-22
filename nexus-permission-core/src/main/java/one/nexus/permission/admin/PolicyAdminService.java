package one.nexus.permission.admin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.AuditPort;
import one.nexus.common.id.UuidV7;
import one.nexus.common.trace.TraceIds;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.engine.DataScopeKind;
import one.nexus.permission.engine.FieldRule;
import one.nexus.permission.engine.PolicySnapshot;
import one.nexus.permission.engine.Rule;
import one.nexus.permission.persist.PolicyRepository;
import one.nexus.permission.redis.PolicyInvalidationPort;
import one.nexus.permission.support.PermissionException;

/** Role / assignment / grant writes. G2 matrix UI reads the same repository. */
public final class PolicyAdminService {
    private final PolicyRepository repository;
    private final PolicyInvalidationPort invalidation;
    private final AuditPort audits;

    public PolicyAdminService(PolicyRepository repository, PolicyInvalidationPort invalidation) {
        this(repository, invalidation, null);
    }

    public PolicyAdminService(
            PolicyRepository repository, PolicyInvalidationPort invalidation, AuditPort audits) {
        this.repository = repository;
        this.invalidation = invalidation;
        this.audits = audits;
    }

    public PolicyRepository.RoleRecord createRole(String code, String name, boolean systemRole) {
        TenantContext context = TenantContextHolder.require();
        if (repository.findRoleByCode(context.tenantId(), code).isPresent()) {
            throw PermissionException.conflict("role code already exists in this tenant");
        }
        PolicyRepository.RoleRecord saved = repository.saveRole(
                new PolicyRepository.RoleRecord(UuidV7.generate(), context.tenantId(), code, name, systemRole, "ACTIVE"));
        bump(context.tenantId());
        return saved;
    }

    public void inherit(UUID childRoleId, UUID parentRoleId) {
        TenantContext context = TenantContextHolder.require();
        PolicyRepository.RoleRecord child = repository.findRole(context.tenantId(), childRoleId)
                .orElseThrow(() -> PermissionException.notFound("child role not found in current tenant"));
        PolicyRepository.RoleRecord parent = repository.findRole(context.tenantId(), parentRoleId)
                .orElseThrow(() -> PermissionException.notFound("parent role not found in current tenant"));
        Set<String> next = new HashSet<>();
        next.add(child.code());
        next.add(parent.code());
        expandInherited(context.tenantId(), next);
        if (PlatformDutyCatalog.conflicting(next)) {
            throw PermissionException.dutyConflict(
                    "platform duty roles cannot inherit another platform duty role");
        }
        repository.addRoleParent(context.tenantId(), childRoleId, parentRoleId);
        bump(context.tenantId());
    }

    public PolicyRepository.AssignmentRecord assign(String principalType, String principalId, UUID roleId) {
        TenantContext context = TenantContextHolder.require();
        PolicyRepository.RoleRecord role = repository.findRole(context.tenantId(), roleId)
                .orElseThrow(() -> PermissionException.notFound("role not found in current tenant"));
        Set<String> next = assignedRoleCodes(context.tenantId(), principalType, principalId);
        next.add(role.code());
        expandInherited(context.tenantId(), next);
        if (PlatformDutyCatalog.conflicting(next)) {
            appendPermissionDenied(
                    context,
                    "role_assignment",
                    role.code(),
                    "duty_conflict",
                    Map.of(
                            "principal_type", principalType,
                            "principal_id", principalId,
                            "role_id", role.id().toString(),
                            "role_code", role.code(),
                            "held_duties", PlatformDutyCatalog.heldDuties(next).toString()));
            throw PermissionException.dutyConflict(
                    "principal cannot hold conflicting platform admin duties in this tenant");
        }
        PolicyRepository.AssignmentRecord saved = repository.saveAssignment(new PolicyRepository.AssignmentRecord(
                UuidV7.generate(), context.tenantId(), principalType, principalId, role.id(), role.code()));
        bump(context.tenantId());
        appendPermission(
                true,
                context,
                "role_assignment",
                role.code(),
                "assigned",
                Map.of(
                        "principal_type", principalType,
                        "principal_id", principalId,
                        "role_id", role.id().toString(),
                        "role_code", role.code()));
        return saved;
    }

    public PolicyRepository.GrantRecord grant(
            String effect,
            String principalType,
            String principalId,
            String resource,
            String action,
            DataScopeKind scopeKind,
            List<String> specifiedDepartments,
            Map<String, FieldRule> fields
    ) {
        TenantContext context = TenantContextHolder.require();
        Rule rule = Rule.scoped(principalType, principalId, resource, action, scopeKind, specifiedDepartments, fields);
        String normalized = effect == null ? "GRANT" : effect.toUpperCase();
        PolicyRepository.GrantRecord saved = repository.saveGrant(new PolicyRepository.GrantRecord(
                UuidV7.generate(), context.tenantId(), normalized, rule));
        bump(context.tenantId());
        appendPermission(
                !"DENY".equalsIgnoreCase(normalized),
                context,
                rule.resource(),
                rule.action(),
                normalized,
                Map.of(
                        "principal_type", rule.principalType(),
                        "principal_id", rule.principalId(),
                        "effect", normalized,
                        "grant_id", saved.id().toString()));
        return saved;
    }

    public PolicyRepository.ShareRecord share(String resource, String recordId, String principalType, String principalId) {
        TenantContext context = TenantContextHolder.require();
        PolicyRepository.ShareRecord saved = repository.saveShare(new PolicyRepository.ShareRecord(
                UuidV7.generate(), context.tenantId(), resource, recordId, principalType, principalId));
        bump(context.tenantId());
        return saved;
    }

    private Set<String> assignedRoleCodes(UUID tenantId, String principalType, String principalId) {
        Set<String> codes = new HashSet<>();
        for (PolicyRepository.AssignmentRecord item : repository.listAssignments(tenantId)) {
            if (principalType.equals(item.principalType()) && principalId.equals(item.principalId())) {
                codes.add(item.roleCode());
            }
        }
        return codes;
    }

    private void expandInherited(UUID tenantId, Set<String> roles) {
        PolicySnapshot snapshot = repository.loadSnapshot(tenantId);
        List<String> pending = new ArrayList<>(roles);
        while (!pending.isEmpty()) {
            String role = pending.removeLast();
            for (String parent : snapshot.roleParents().getOrDefault(role, Set.of())) {
                if (roles.add(parent)) {
                    pending.add(parent);
                }
            }
        }
    }

    private void bump(UUID tenantId) {
        repository.bumpVersion(tenantId);
        invalidation.notifyTenant(tenantId.toString());
    }

    private void appendPermission(
            boolean granted,
            TenantContext context,
            String resourceType,
            String resourceId,
            String reason,
            Map<String, ?> details) {
        if (audits == null) {
            return;
        }
        audits.append(AuditCaptures.permission(
                granted,
                context.tenantId(),
                context.personId(),
                resourceType,
                resourceId,
                reason,
                TraceIds.currentOrUnknown(),
                details));
    }

    private void appendPermissionDenied(
            TenantContext context,
            String resourceType,
            String resourceId,
            String reason,
            Map<String, ?> details) {
        if (audits == null) {
            return;
        }
        audits.appendNewTransaction(AuditCaptures.permission(
                false,
                context.tenantId(),
                context.personId(),
                resourceType,
                resourceId,
                reason,
                TraceIds.currentOrUnknown(),
                details));
    }
}
