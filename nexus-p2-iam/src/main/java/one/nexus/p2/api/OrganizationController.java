package one.nexus.p2.api;

import one.nexus.p2.IdentityModule;
import one.nexus.p2.account.AccountPersonMaster;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.domain.Model;
import one.nexus.p2.org.OrganizationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class OrganizationController {
    private final IdentityModule module;

    public OrganizationController(IdentityModule module) {
        this.module = module;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "module", "nexus-p2-iam");
    }

    @PostMapping("/persons")
    public Model.Person createPerson(@RequestBody PersonBody body) {
        return module.organization().createPerson(new OrganizationService.PersonCommand(
                body.displayName(), body.employeeNo(), body.email(), body.phone(), body.hiredOn(), body.accountId()));
    }

    @GetMapping("/persons")
    public List<Model.Person> listPersons() {
        return module.organization().listPersons();
    }

    @GetMapping("/persons/{id}")
    public Model.Person getPerson(@PathVariable UUID id) {
        return module.organization().getPerson(id);
    }

    @PostMapping("/persons/{id}/disable")
    public Model.Person disablePerson(@PathVariable UUID id) {
        return module.organization().disablePerson(id);
    }

    @PostMapping("/persons/{id}/invite")
    public Map<String, String> invite(@PathVariable UUID id) {
        Model.Person person = module.organization().getPerson(id);
        Model.GlobalAccount account = module.repository().findAccount(person.accountId())
                .orElseThrow(() -> one.nexus.p2.support.DomainException.notFound("global account not found"));
        module.smtp().invite(account.keycloakUserId());
        return Map.of("status", "submitted");
    }

    @PostMapping("/departments")
    public Model.Department createDepartment(@RequestBody DepartmentBody body) {
        return module.organization().createDepartment(new OrganizationService.DepartmentCommand(
                body.code(), body.name(), body.parentId(), body.sortOrder() == null ? 0 : body.sortOrder(), body.leaderPersonId()));
    }

    @GetMapping("/departments/tree")
    public List<OrganizationService.DepartmentNode> tree() {
        return module.organization().departmentTree();
    }

    @PostMapping("/departments/{id}/disable")
    public Model.Department disableDepartment(@PathVariable UUID id) {
        return module.organization().disableDepartment(id);
    }

    @PostMapping("/positions")
    public Model.Position createPosition(@RequestBody PositionBody body) {
        return module.organization().createPosition(new OrganizationService.PositionCommand(
                body.code(), body.name(), body.category(), body.duties()));
    }

    @GetMapping("/positions")
    public List<Model.Position> listPositions() {
        return module.organization().listPositions();
    }

    @PostMapping("/positions/{id}/disable")
    public Model.Position disablePosition(@PathVariable UUID id) {
        return module.organization().disablePosition(id);
    }

    @PostMapping("/employments")
    public Model.Employment assign(@RequestBody EmploymentBody body) {
        return module.organization().assign(new OrganizationService.EmploymentCommand(
                body.personId(), body.departmentId(), body.positionId(),
                body.kind() == null ? Model.EmploymentKind.PRIMARY : body.kind(),
                body.validFrom(), body.validTo()));
    }

    @GetMapping("/employments")
    public List<Model.Employment> employments() {
        return module.organization().listEmployments();
    }

    @GetMapping("/persons/{id}/employments")
    public List<Model.Employment> employments(@PathVariable UUID id) {
        return module.organization().listEmployments(id);
    }

    @PostMapping("/employments/{id}/end")
    public Model.Employment end(@PathVariable UUID id) {
        return module.organization().endEmployment(id);
    }

    @GetMapping("/memberships")
    public List<Model.TenantMembership> memberships() {
        return module.memberships().listCurrentTenant();
    }

    @PostMapping("/memberships")
    public Model.TenantMembership addMembership(@RequestBody MembershipBody body) {
        return module.memberships().add(body.accountId());
    }


    /**
     * T1-L1: ensure a loginable person/account master for a tenant (no org tree).
     * Bootstrap-token gated; uses existing tenant id conventions (T1-L0 owned elsewhere).
     */
    @PostMapping("/internal/tenants/{tenantId}/loginable-persons")
    public AccountPersonMaster.Result ensureLoginablePerson(@PathVariable UUID tenantId, @RequestBody LoginablePersonBody body) {
        return module.accountPersonMaster().ensure(new AccountPersonMaster.Command(
                tenantId, body.displayName(), body.email(), body.username(), body.employeeNo(), body.password()));
    }

    @PostMapping("/internal/tenants/{tenantId}/admin-users")
    public TenantAdminInitializer.Result bootstrapAdmin(@PathVariable UUID tenantId, @RequestBody AdminBody body) {
        return module.adminInitializer().initialize(new TenantAdminInitializer.Command(
                tenantId, body.displayName(), body.email(), body.username()));
    }

    public record PersonBody(String displayName, String employeeNo, String email, String phone, LocalDate hiredOn, UUID accountId) {}
    public record DepartmentBody(String code, String name, UUID parentId, Integer sortOrder, UUID leaderPersonId) {}
    public record PositionBody(String code, String name, String category, String duties) {}
    public record EmploymentBody(UUID personId, UUID departmentId, UUID positionId, Model.EmploymentKind kind, LocalDate validFrom, LocalDate validTo) {}
    public record MembershipBody(UUID accountId) {}
    public record LoginablePersonBody(String displayName, String email, String username, String employeeNo, String password) {}
    public record AdminBody(String displayName, String email, String username) {}
}
