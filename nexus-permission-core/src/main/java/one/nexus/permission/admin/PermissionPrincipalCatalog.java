package one.nexus.permission.admin;

import java.util.Comparator;
import java.util.List;
import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.domain.Model;
import one.nexus.p2.kernel.TenantContextHolder;

/** Current-tenant principals exposed to the RBAC administration surface. */
public final class PermissionPrincipalCatalog {
    private final IdentityRepository identity;

    public PermissionPrincipalCatalog(IdentityRepository identity) {
        this.identity = identity;
    }

    public PrincipalView view() {
        if (identity == null) {
            return new PrincipalView(List.of(), List.of(), List.of());
        }
        var tenantId = TenantContextHolder.require().tenantId();
        List<PrincipalOption> users = identity.listPersons(tenantId).stream()
                .filter(item -> item.status() == Model.PersonStatus.ACTIVE && item.deletedAt() == null)
                .map(item -> new PrincipalOption(item.id().toString(), item.displayName(), item.employeeNo()))
                .sorted(Comparator.comparing(PrincipalOption::label))
                .toList();
        List<PrincipalOption> positions = identity.listPositions(tenantId).stream()
                .filter(item -> item.status() == Model.OrgStatus.ACTIVE && item.deletedAt() == null)
                .map(item -> new PrincipalOption(item.id().toString(), item.name(), item.code()))
                .sorted(Comparator.comparing(PrincipalOption::label))
                .toList();
        List<PrincipalOption> departments = identity.listDepartments(tenantId).stream()
                .filter(item -> item.status() == Model.OrgStatus.ACTIVE && item.deletedAt() == null)
                .map(item -> new PrincipalOption(item.id().toString(), item.name(), item.code()))
                .sorted(Comparator.comparing(PrincipalOption::label))
                .toList();
        return new PrincipalView(users, positions, departments);
    }

    public record PrincipalOption(String id, String label, String code) {}

    public record PrincipalView(
            List<PrincipalOption> users,
            List<PrincipalOption> positions,
            List<PrincipalOption> departments) {}
}
