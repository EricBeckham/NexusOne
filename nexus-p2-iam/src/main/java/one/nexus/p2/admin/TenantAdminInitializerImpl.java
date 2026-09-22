package one.nexus.p2.admin;

import one.nexus.p2.account.AccountReconciler;
import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.domain.Model;
import one.nexus.p2.idp.IdentityProviderPort;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.org.OrganizationService;
import one.nexus.p2.support.DomainException;
import one.nexus.p2.support.Ids;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class TenantAdminInitializerImpl implements TenantAdminInitializer {
    private final IdentityRepository repository;
    private final IdentityProviderPort idp;
    private final AccountReconciler reconciler;
    private final OrganizationService organization;
    private final Clock clock;

    public TenantAdminInitializerImpl(
            IdentityRepository repository,
            IdentityProviderPort idp,
            AccountReconciler reconciler,
            OrganizationService organization,
            Clock clock
    ) {
        this.repository = repository;
        this.idp = idp;
        this.reconciler = reconciler;
        this.organization = organization;
        this.clock = clock;
    }

    @Override
    public Result initialize(Command command) {
        if (command.tenantId() == null) {
            throw DomainException.badRequest("P2.IAM.TENANT_REQUIRED", "tenantId is required");
        }
        if (command.email() == null || command.email().isBlank()) {
            throw DomainException.badRequest("P2.IAM.EMAIL_REQUIRED", "email is required");
        }
        String username = command.username() == null || command.username().isBlank() ? command.email() : command.username();
        String displayName = command.displayName() == null || command.displayName().isBlank() ? username : command.displayName();

        IdentityProviderPort.ProvisionedUser provisioned = idp.ensureUser(command.email(), username, displayName);
        Model.GlobalAccount account = reconciler.reconcile(new IdentityProviderPort.IdentityClaims(
                provisioned.subject(), command.email(), username, displayName, null));

        Instant now = clock.instant();
        Model.TenantMembership membership = repository.findMembership(command.tenantId(), account.id())
                .orElseGet(() -> repository.saveMembership(new Model.TenantMembership(
                        Ids.v7(clock), command.tenantId(), account.id(),
                        Model.MembershipStatus.ACTIVE, now, now, null, 0)));

        Model.Person person = repository.findPersonByAccount(command.tenantId(), account.id())
                .orElseGet(() -> repository.savePerson(new Model.Person(
                        Ids.v7(clock), command.tenantId(), account.id(), displayName, "ADMIN",
                        command.email(), null, null, Model.PersonStatus.ACTIVE, now, now, null, 0)));

        TenantContext previous = TenantContextHolder.get();
        TenantContextHolder.set(new TenantContext(
                Ids.v7(clock), command.tenantId(), account.id(), person.id(),
                provisioned.subject(), now, now.plus(Duration.ofHours(1))));
        try {
            Model.Department department = repository.findDepartmentByCode(command.tenantId(), "ROOT")
                    .orElseGet(() -> organization.createDepartment(
                            new OrganizationService.DepartmentCommand("ROOT", "Root", null, 0, person.id())));
            Model.Position position = repository.findPositionByCode(command.tenantId(), "TENANT_ADMIN")
                    .orElseGet(() -> organization.createPosition(
                            new OrganizationService.PositionCommand("TENANT_ADMIN", "Tenant Admin", "admin", "Tenant administrator")));
            Model.Employment employment = repository.findPrimaryEmployment(command.tenantId(), person.id())
                    .orElseGet(() -> organization.assign(new OrganizationService.EmploymentCommand(
                            person.id(), department.id(), position.id(),
                            Model.EmploymentKind.PRIMARY, null, null)));
            return new Result(
                    command.tenantId(), account.id(), person.id(), membership.id(),
                    department.id(), position.id(), employment.id(), provisioned.subject());
        } finally {
            if (previous == null) {
                TenantContextHolder.clear();
            } else {
                TenantContextHolder.set(previous);
            }
        }
    }
}
