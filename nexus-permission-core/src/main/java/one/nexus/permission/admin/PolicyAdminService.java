package one.nexus.permission.admin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.common.id.UuidV7;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.engine.DataScopeKind;
import one.nexus.permission.engine.FieldRule;
import one.nexus.permission.engine.Rule;
import one.nexus.permission.persist.PolicyRepository;
import one.nexus.permission.redis.PolicyInvalidationPort;
import one.nexus.permission.support.PermissionException;

/** Role / assignment / grant writes. Full admin UI is M0-12. */
public final class PolicyAdminService {
    private final PolicyRepository repository;
    private final PolicyInvalidationPort invalidation;

    public PolicyAdminService(PolicyRepository repository, PolicyInvalidationPort invalidation) {
        this.repository = repository;
        this.invalidation = invalidation;
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
        repository.addRoleParent(context.tenantId(), childRoleId, parentRoleId);
        bump(context.tenantId());
    }

    public PolicyRepository.AssignmentRecord assign(String principalType, String principalId, UUID roleId) {
        TenantContext context = TenantContextHolder.require();
        PolicyRepository.RoleRecord role = repository.findRole(context.tenantId(), roleId)
                .orElseThrow(() -> PermissionException.notFound("role not found in current tenant"));
        PolicyRepository.AssignmentRecord saved = repository.saveAssignment(new PolicyRepository.AssignmentRecord(
                UuidV7.generate(), context.tenantId(), principalType, principalId, role.id(), role.code()));
        bump(context.tenantId());
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
        PolicyRepository.GrantRecord saved = repository.saveGrant(new PolicyRepository.GrantRecord(
                UuidV7.generate(), context.tenantId(), effect == null ? "GRANT" : effect.toUpperCase(), rule));
        bump(context.tenantId());
        return saved;
    }

    public PolicyRepository.ShareRecord share(String resource, String recordId, String principalType, String principalId) {
        TenantContext context = TenantContextHolder.require();
        PolicyRepository.ShareRecord saved = repository.saveShare(new PolicyRepository.ShareRecord(
                UuidV7.generate(), context.tenantId(), resource, recordId, principalType, principalId));
        bump(context.tenantId());
        return saved;
    }

    private void bump(UUID tenantId) {
        repository.bumpVersion(tenantId);
        invalidation.notifyTenant(tenantId.toString());
    }
}
