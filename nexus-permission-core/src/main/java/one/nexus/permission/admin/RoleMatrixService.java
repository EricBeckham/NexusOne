package one.nexus.permission.admin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.PermissionService;
import one.nexus.permission.engine.DataScopeKind;
import one.nexus.permission.engine.FieldRule;
import one.nexus.permission.engine.PolicySnapshot;
import one.nexus.permission.persist.PolicyRepository;
import one.nexus.permission.persist.PolicyRepository.AssignmentRecord;
import one.nexus.permission.persist.PolicyRepository.GrantRecord;
import one.nexus.permission.persist.PolicyRepository.RoleRecord;

/** Reads and installs the tenant role matrix. Writes go through {@link PolicyAdminService}. */
public final class RoleMatrixService {
    private final PolicyRepository repository;
    private final PolicyAdminService admin;
    private final PermissionService service;

    public RoleMatrixService(
            PolicyRepository repository, PolicyAdminService admin, PermissionService service) {
        this.repository = repository;
        this.admin = admin;
        this.service = service;
    }

    public MatrixView view() {
        TenantContext context = TenantContextHolder.require();
        UUID tenantId = context.tenantId();
        PolicySnapshot snapshot = service.snapshot();
        List<RoleRecord> roles = repository.listRoles(tenantId);
        List<AssignmentRecord> assignments = repository.listAssignments(tenantId);
        List<CellView> cells = new ArrayList<>();
        for (GrantRecord grant : repository.listGrants(tenantId)) {
            cells.add(CellView.from(grant));
        }
        boolean demoSeed = roles.stream().anyMatch(role -> TenantMemberGrantSeeder.ROLE_CODE.equals(role.code()));
        boolean hasScopedOrFielded = cells.stream().anyMatch(CellView::differentiated);
        return new MatrixView(
                tenantId.toString(),
                snapshot.version(),
                demoSeed,
                hasScopedOrFielded,
                RoleMatrixCatalog.COMPLETENESS_NOTE,
                roles.stream().map(RoleView::from).toList(),
                assignments.stream().map(AssignmentView::from).toList(),
                List.copyOf(cells));
    }

    /**
     * Creates the G2 example roles and cells if missing. Does not assign them
     * to the current person — that is the difference from {@code tenant_member}.
     */
    public MatrixView installExample() {
        TenantContextHolder.require();
        Map<String, RoleRecord> byCode = new LinkedHashMap<>();
        for (RoleMatrixCatalog.RoleSpec spec : RoleMatrixCatalog.exampleRoles()) {
            RoleRecord role = repository.findRoleByCode(TenantContextHolder.require().tenantId(), spec.code())
                    .orElseGet(() -> admin.createRole(spec.code(), spec.name(), true));
            byCode.put(spec.code(), role);
        }
        UUID tenantId = TenantContextHolder.require().tenantId();
        List<GrantRecord> existing = repository.listGrants(tenantId);
        for (RoleMatrixCatalog.CellSpec cell : RoleMatrixCatalog.exampleCells()) {
            boolean present = existing.stream().anyMatch(grant -> matches(grant, cell));
            if (!present) {
                admin.grant(
                        cell.effect(),
                        "role",
                        cell.roleCode(),
                        cell.resource(),
                        cell.action(),
                        cell.scopeKind(),
                        List.of(),
                        cell.fields());
            }
        }
        return view();
    }

    public List<RoleRecord> roles() {
        return repository.listRoles(TenantContextHolder.require().tenantId());
    }

    public List<AssignmentRecord> assignments() {
        return repository.listAssignments(TenantContextHolder.require().tenantId());
    }

    public List<CellView> grants() {
        return repository.listGrants(TenantContextHolder.require().tenantId()).stream()
                .map(CellView::from)
                .toList();
    }

    private static boolean matches(GrantRecord grant, RoleMatrixCatalog.CellSpec cell) {
        if (grant.rule() == null) {
            return false;
        }
        return cell.effect().equalsIgnoreCase(grant.effect())
                && "role".equals(grant.rule().principalType())
                && cell.roleCode().equals(grant.rule().principalId())
                && cell.resource().equals(grant.rule().resource())
                && cell.action().equals(grant.rule().action());
    }

    public record MatrixView(
            String tenantId,
            long policyVersion,
            boolean demoSeedPresent,
            boolean differentiatedPolicy,
            String completenessNote,
            List<RoleView> roles,
            List<AssignmentView> assignments,
            List<CellView> cells
    ) {}

    public record RoleView(String id, String code, String name, boolean systemRole, String status) {
        public static RoleView from(RoleRecord role) {
            return new RoleView(role.id().toString(), role.code(), role.name(), role.systemRole(), role.status());
        }
    }

    public record AssignmentView(
            String id, String principalType, String principalId, String roleId, String roleCode) {
        public static AssignmentView from(AssignmentRecord item) {
            return new AssignmentView(
                    item.id().toString(),
                    item.principalType(),
                    item.principalId(),
                    item.roleId().toString(),
                    item.roleCode());
        }
    }

    public record CellView(
            String id,
            String effect,
            String principalType,
            String principalId,
            String resource,
            String action,
            String scopeKind,
            List<String> specifiedDepartments,
            Map<String, FieldRule> fields
    ) {
        static CellView from(GrantRecord grant) {
            var rule = grant.rule();
            DataScopeKind kind = rule.scopeKind();
            return new CellView(
                    grant.id().toString(),
                    grant.effect(),
                    rule.principalType(),
                    rule.principalId(),
                    rule.resource(),
                    rule.action(),
                    kind == null ? DataScopeKind.CUSTOM.name() : kind.name(),
                    rule.specifiedDepartmentIds(),
                    rule.fields());
        }

        boolean differentiated() {
            boolean notAll = scopeKind != null && !DataScopeKind.ALL.name().equals(scopeKind);
            return notAll || (fields != null && !fields.isEmpty());
        }
    }
}
