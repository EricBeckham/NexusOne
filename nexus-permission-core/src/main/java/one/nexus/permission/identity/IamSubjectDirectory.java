package one.nexus.permission.identity;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.domain.Model;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.permission.engine.DepartmentCatalog;
import one.nexus.permission.engine.Subject;
import one.nexus.permission.persist.PolicyRepository;
import one.nexus.permission.support.PermissionException;

/**
 * Builds a Subject from M0-04 Person / Employment / Department plus P2 role
 * assignments. Uses the issued TenantContext; never a client tenant header.
 */
public final class IamSubjectDirectory implements SubjectDirectory {
    private final IdentityRepository identity;
    private final PolicyRepository policies;

    public IamSubjectDirectory(IdentityRepository identity, PolicyRepository policies) {
        this.identity = identity;
        this.policies = policies;
    }

    @Override
    public Subject resolve(TenantContext context) {
        if (context == null) {
            throw PermissionException.missingContext();
        }
        UUID tenantId = context.tenantId();
        UUID personId = context.personId();
        identity.findPerson(tenantId, personId)
                .orElseThrow(() -> PermissionException.notFound("person not found in current tenant"));
        List<Model.Employment> employments = identity.listEmploymentsByPerson(tenantId, personId);
        Set<String> departments = new LinkedHashSet<>();
        Set<String> positions = new LinkedHashSet<>();
        Map<String, String> paths = new LinkedHashMap<>();
        for (Model.Employment employment : employments) {
            if (employment.status() != Model.EmploymentStatus.ACTIVE || employment.deletedAt() != null) {
                continue;
            }
            departments.add(employment.departmentId().toString());
            positions.add(employment.positionId().toString());
            identity.findDepartment(tenantId, employment.departmentId())
                    .ifPresent(department -> paths.put(department.id().toString(), department.path()));
        }
        Set<String> roles = policies.roleCodesFor(
                tenantId, personId.toString(), positions, departments);
        return new Subject(tenantId.toString(), personId.toString(), roles, positions, departments, paths);
    }

    @Override
    public DepartmentCatalog catalog(UUID tenantId) {
        Map<String, String> paths = new LinkedHashMap<>();
        for (Model.Department department : identity.listDepartments(tenantId)) {
            if (department.deletedAt() == null) {
                paths.put(department.id().toString(), department.path());
            }
        }
        return new DepartmentCatalog(paths);
    }
}
