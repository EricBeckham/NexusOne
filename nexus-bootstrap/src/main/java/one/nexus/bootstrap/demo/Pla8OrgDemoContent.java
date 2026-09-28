package one.nexus.bootstrap.demo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.domain.Model;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.org.OrganizationService;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.persist.PolicyRepository.RoleRecord;
import one.nexus.permission.position.PositionRoleService;

/**
 * PLA8. Demo organization for the fixed P1 tenant, written only through the identity and permission APIs.
 * Application roles {@code app_*} are landed by a later product subscription (PLB9) and bound after that.
 */
public final class Pla8OrgDemoContent {
    public static final String DEPARTMENT = "pl_office";
    public static final String POSITION_ADMIN = "tenant_admin";
    public static final String POSITION_LEAD = "pl_lead";
    public static final String POSITION_MEMBER = "pl_member";
    public static final String EMPLOYEE_ADMIN = "E-PL-ADMIN";
    public static final String EMPLOYEE_LEAD = "E-PL-LEAD";
    public static final String EMPLOYEE_MEMBER = "E-PL-MEMBER";

    static final UUID ACCOUNT_ADMIN = UUID.fromString("01900000-0000-7000-8000-000000000a01");
    static final UUID ACCOUNT_LEAD = UUID.fromString("01900000-0000-7000-8000-000000000a02");
    static final UUID ACCOUNT_MEMBER = UUID.fromString("01900000-0000-7000-8000-000000000a03");
    private static final LocalDate HIRED = LocalDate.of(2026, 1, 1);

    private Pla8OrgDemoContent() {}

    public static void write(IdentityModule identity, PermissionModule permissions, TenantMemberGrantSeeder seeder,
            UUID tenantId) {
        TenantContext previous = TenantContextHolder.get();
        try {
            TenantContextHolder.set(context(tenantId, ACCOUNT_ADMIN, null));
            OrganizationService org = identity.organization();
            IdentityRepository repository = identity.repository();
            Model.Person admin = person(org, repository, tenantId, "陈管理", EMPLOYEE_ADMIN, "pl-admin@demo.invalid", ACCOUNT_ADMIN);
            Model.Person lead = person(org, repository, tenantId, "周审批", EMPLOYEE_LEAD, "pl-lead@demo.invalid", ACCOUNT_LEAD);
            Model.Person member = person(org, repository, tenantId, "林办理", EMPLOYEE_MEMBER, "pl-member@demo.invalid", ACCOUNT_MEMBER);
            Model.Department office = department(org, repository, tenantId, lead.id());
            Model.Position adminPosition = position(org, repository, tenantId, POSITION_ADMIN, "租户管理员", "管理");
            Model.Position leadPosition = position(org, repository, tenantId, POSITION_LEAD, "部门负责人", "审批");
            Model.Position memberPosition = position(org, repository, tenantId, POSITION_MEMBER, "成员", "办理");
            employ(org, repository, tenantId, admin.id(), office.id(), adminPosition.id());
            employ(org, repository, tenantId, lead.id(), office.id(), leadPosition.id());
            employ(org, repository, tenantId, member.id(), office.id(), memberPosition.id());

            TenantContextHolder.set(context(tenantId, ACCOUNT_ADMIN, admin.id()));
            seeder.ensure();
            RoleRecord memberRole = permissions.repository().findRoleByCode(tenantId, TenantMemberGrantSeeder.ROLE_CODE)
                    .orElseThrow(() -> new IllegalStateException("tenant_member was not seeded"));
            RoleRecord designer = permissions.repository().findRoleByCode(tenantId, TenantMemberGrantSeeder.DESIGNER_ROLE)
                    .orElseThrow(() -> new IllegalStateException("tenant_process_designer was not seeded"));
            PositionRoleService bindings = new PositionRoleService(permissions, repository);
            bindings.set(memberPosition.id(), List.of(memberRole.id()));
            bindings.set(leadPosition.id(), List.of(memberRole.id()));
            bindings.set(adminPosition.id(), List.of(memberRole.id(), designer.id()));
        } finally {
            if (previous == null) {
                TenantContextHolder.clear();
            } else {
                TenantContextHolder.set(previous);
            }
        }
    }

    private static Model.Department department(OrganizationService org, IdentityRepository repository, UUID tenantId,
            UUID leaderId) {
        return repository.findDepartmentByCode(tenantId, DEPARTMENT).orElseGet(() -> org.createDepartment(
                new OrganizationService.DepartmentCommand(DEPARTMENT, "办公", null, 0, leaderId)));
    }

    private static Model.Position position(OrganizationService org, IdentityRepository repository, UUID tenantId,
            String code, String name, String duties) {
        return repository.findPositionByCode(tenantId, code).orElseGet(() -> org.createPosition(
                new OrganizationService.PositionCommand(code, name, "GENERAL", duties)));
    }

    private static Model.Person person(OrganizationService org, IdentityRepository repository, UUID tenantId,
            String name, String employeeNo, String email, UUID accountId) {
        ensureAccount(repository, tenantId, accountId, name, employeeNo, email);
        return repository.findPersonByEmployeeNo(tenantId, employeeNo).orElseGet(() -> org.createPerson(
                new OrganizationService.PersonCommand(name, employeeNo, email, null, HIRED, accountId)));
    }

    /**
     * A person's account must exist first ({@code iam_person_account_id_fkey}); the in-memory repository used by
     * unit tests has no foreign keys, so only a PostgreSQL boot showed it. Written through the identity repository,
     * idempotent, same shape as the P2 demo accounts.
     */
    private static void ensureAccount(IdentityRepository repository, UUID tenantId, UUID accountId, String name,
            String employeeNo, String email) {
        Instant since = HIRED.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        String handle = employeeNo.toLowerCase(java.util.Locale.ROOT);
        if (repository.findAccount(accountId).isEmpty()) {
            repository.saveAccount(new Model.GlobalAccount(accountId, "demo-seed:pla8:" + handle, email, null,
                    "pla8-" + handle, name, Model.AccountStatus.ACTIVE, since, since, null, 0));
        }
        if (repository.findMembership(tenantId, accountId).isEmpty()) {
            repository.saveMembership(new Model.TenantMembership(
                    UUID.nameUUIDFromBytes(("pla8-membership:" + tenantId + ":" + accountId).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    tenantId, accountId, Model.MembershipStatus.ACTIVE, since, since, null, 0));
        }
    }

    private static void employ(OrganizationService org, IdentityRepository repository, UUID tenantId, UUID personId,
            UUID departmentId, UUID positionId) {
        if (repository.findPrimaryEmployment(tenantId, personId).isPresent()) {
            return;
        }
        org.assign(new OrganizationService.EmploymentCommand(
                personId, departmentId, positionId, Model.EmploymentKind.PRIMARY, HIRED, null));
    }

    private static TenantContext context(UUID tenantId, UUID accountId, UUID personId) {
        Instant now = Instant.now();
        return new TenantContext(UUID.randomUUID(), tenantId, accountId, personId, "pla8-demo", now, now.plusSeconds(600));
    }
}
