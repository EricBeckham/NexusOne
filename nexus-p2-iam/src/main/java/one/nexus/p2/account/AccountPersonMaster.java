package one.nexus.p2.account;

import one.nexus.common.id.UuidV7;
import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.domain.Model;
import one.nexus.p2.idp.IdentityProviderPort;
import one.nexus.p2.support.DomainException;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * T1-L1/L2 minimal account/person master for phase-1 own-user login.
 * Ensures IdP subject <-> GlobalAccount <-> TenantMembership <-> Person so
 * {@link one.nexus.p2.session.SessionService} can issue a tenant context.
 * Does not create org tree (dept/position/employment); that stays with
 * {@link one.nexus.p2.admin.TenantAdminInitializer} / org CRUD.
 */
public final class AccountPersonMaster {
    public record Command(
            UUID tenantId,
            String displayName,
            String email,
            String username,
            String employeeNo,
            String password
    ) {
        /** T1-L1 callers that do not set a password. */
        public Command(UUID tenantId, String displayName, String email, String username, String employeeNo) {
            this(tenantId, displayName, email, username, employeeNo, null);
        }
    }

    public record Result(
            UUID tenantId,
            UUID accountId,
            UUID personId,
            UUID membershipId,
            String idpSubject,
            boolean createdAccount,
            boolean createdMembership,
            boolean createdPerson
    ) {}

    private final IdentityRepository repository;
    private final IdentityProviderPort idp;
    private final AccountReconciler accounts;
    private final Clock clock;

    public AccountPersonMaster(
            IdentityRepository repository,
            IdentityProviderPort idp,
            AccountReconciler accounts,
            Clock clock
    ) {
        this.repository = repository;
        this.idp = idp;
        this.accounts = accounts;
        this.clock = clock;
    }

    public Result ensure(Command command) {
        if (command.tenantId() == null) {
            throw DomainException.badRequest("P2.IAM.TENANT_REQUIRED", "tenantId is required");
        }
        if (command.email() == null || command.email().isBlank()) {
            throw DomainException.badRequest("P2.IAM.EMAIL_REQUIRED", "email is required");
        }
        String username = blankTo(command.username(), command.email());
        String displayName = blankTo(command.displayName(), username);

        boolean createdAccount = repository.findAccountByEmail(command.email()).isEmpty();
        IdentityProviderPort.ProvisionedUser provisioned = idp.ensureUser(command.email(), username, displayName);
        if (command.password() != null && !command.password().isBlank()) {
            idp.setPassword(provisioned.subject(), command.password());
        }
        Model.GlobalAccount account = accounts.reconcile(new IdentityProviderPort.IdentityClaims(
                provisioned.subject(), command.email(), username, displayName, null));
        if (account.status() != Model.AccountStatus.ACTIVE) {
            throw DomainException.forbidden("account is not active");
        }

        Instant now = clock.instant();
        boolean createdMembership = repository.findMembership(command.tenantId(), account.id()).isEmpty();
        Model.TenantMembership membership = repository.findMembership(command.tenantId(), account.id())
                .orElseGet(() -> repository.saveMembership(new Model.TenantMembership(
                        UuidV7.generate(), command.tenantId(), account.id(),
                        Model.MembershipStatus.ACTIVE, now, now, null, 0)));
        if (membership.status() != Model.MembershipStatus.ACTIVE) {
            throw DomainException.forbidden("tenant membership is not active");
        }

        boolean createdPerson = repository.findPersonByAccount(command.tenantId(), account.id()).isEmpty();
        Model.Person person = repository.findPersonByAccount(command.tenantId(), account.id())
                .orElseGet(() -> repository.savePerson(new Model.Person(
                        UuidV7.generate(),
                        command.tenantId(),
                        account.id(),
                        displayName,
                        blankToNull(command.employeeNo()),
                        command.email(),
                        null,
                        null,
                        Model.PersonStatus.ACTIVE,
                        now,
                        now,
                        null,
                        0)));
        if (person.status() != Model.PersonStatus.ACTIVE) {
            throw DomainException.forbidden("person is not active");
        }

        return new Result(
                command.tenantId(),
                account.id(),
                person.id(),
                membership.id(),
                provisioned.subject(),
                createdAccount,
                createdMembership,
                createdPerson);
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
