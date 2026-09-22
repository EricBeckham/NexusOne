package one.nexus.permission.admin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.PermissionService;
import one.nexus.permission.engine.PolicySnapshot;
import one.nexus.permission.persist.PolicyRepository;
import one.nexus.permission.persist.PolicyRepository.GrantRecord;
import one.nexus.permission.persist.PolicyRepository.RoleRecord;

/** Installs and reads the three-power platform duty catalog. */
public final class PlatformDutyService {
    private final PolicyRepository repository;
    private final PolicyAdminService admin;
    private final PermissionService service;

    public PlatformDutyService(
            PolicyRepository repository, PolicyAdminService admin, PermissionService service) {
        this.repository = repository;
        this.admin = admin;
        this.service = service;
    }

    public DutyView view() {
        TenantContextHolder.require();
        UUID tenantId = TenantContextHolder.require().tenantId();
        PolicySnapshot snapshot = service.snapshot();
        List<RoleRecord> roles = repository.listRoles(tenantId);
        List<RoleMatrixService.RoleView> installed = roles.stream()
                .filter(role -> PlatformDutyCatalog.isDutyRole(role.code()))
                .map(RoleMatrixService.RoleView::from)
                .toList();
        List<RoleMatrixService.CellView> cells = new ArrayList<>();
        for (GrantRecord grant : repository.listGrants(tenantId)) {
            if (grant.rule() != null && PlatformDutyCatalog.isDutyRole(grant.rule().principalId())) {
                cells.add(RoleMatrixService.CellView.from(grant));
            }
        }
        boolean complete = installed.size() == PlatformDutyCatalog.DUTY_ROLES.size();
        return new DutyView(
                tenantId.toString(),
                snapshot.version(),
                complete,
                PlatformDutyCatalog.COMPLETENESS_NOTE,
                PlatformDutyCatalog.roles(),
                PlatformDutyCatalog.exclusivePairs(),
                installed,
                List.copyOf(cells));
    }

    /**
     * Creates the three duty roles and their grant/deny cells if missing.
     * Does not assign them to the current person.
     */
    public DutyView install() {
        TenantContextHolder.require();
        Map<String, RoleRecord> byCode = new LinkedHashMap<>();
        for (PlatformDutyCatalog.RoleSpec spec : PlatformDutyCatalog.roles()) {
            RoleRecord role = repository.findRoleByCode(TenantContextHolder.require().tenantId(), spec.code())
                    .orElseGet(() -> admin.createRole(spec.code(), spec.name(), true));
            byCode.put(spec.code(), role);
        }
        UUID tenantId = TenantContextHolder.require().tenantId();
        List<GrantRecord> existing = repository.listGrants(tenantId);
        for (PlatformDutyCatalog.CellSpec cell : PlatformDutyCatalog.cells()) {
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
                        Map.of());
            }
        }
        return view();
    }

    private static boolean matches(GrantRecord grant, PlatformDutyCatalog.CellSpec cell) {
        if (grant.rule() == null) {
            return false;
        }
        return cell.effect().equalsIgnoreCase(grant.effect())
                && "role".equals(grant.rule().principalType())
                && cell.roleCode().equals(grant.rule().principalId())
                && cell.resource().equals(grant.rule().resource())
                && cell.action().equals(grant.rule().action());
    }

    public record DutyView(
            String tenantId,
            long policyVersion,
            boolean catalogInstalled,
            String completenessNote,
            List<PlatformDutyCatalog.RoleSpec> catalog,
            List<List<String>> exclusivePairs,
            List<RoleMatrixService.RoleView> roles,
            List<RoleMatrixService.CellView> cells
    ) {}
}
