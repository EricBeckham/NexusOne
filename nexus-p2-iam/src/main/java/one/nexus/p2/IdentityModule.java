package one.nexus.p2;

import one.nexus.p2.account.AccountReconciler;
import one.nexus.p2.account.PersonIdentityReconciler;
import one.nexus.p2.admin.TenantAdminInitializer;
import one.nexus.p2.admin.TenantAdminInitializerImpl;
import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.idp.IdentityProviderPort;
import one.nexus.p2.membership.MembershipService;
import one.nexus.p2.org.OrganizationService;
import one.nexus.p2.session.SessionService;
import one.nexus.p2.smtp.SmtpHook;

import java.time.Clock;
import java.time.Duration;

/** Composition root so tests and Spring share the same wiring. */
public final class IdentityModule {
    private final IdentityRepository repository;
    private final IdentityProviderPort idp;
    private final AccountReconciler reconciler;
    private final PersonIdentityReconciler personReconciler;
    private final SessionService sessions;
    private final OrganizationService organization;
    private final MembershipService memberships;
    private final TenantAdminInitializer adminInitializer;
    private final SmtpHook smtp;

    public IdentityModule(
            IdentityRepository repository,
            IdentityProviderPort idp,
            SmtpHook smtp,
            Clock clock,
            Duration contextTtl
    ) {
        this.repository = repository;
        this.idp = idp;
        this.smtp = smtp;
        this.reconciler = new AccountReconciler(repository, clock);
        this.personReconciler = new PersonIdentityReconciler(repository, idp, this.reconciler, clock);
        this.organization = new OrganizationService(repository, clock);
        this.memberships = new MembershipService(repository, clock);
        this.sessions = new SessionService(repository, reconciler, idp, clock, contextTtl);
        this.adminInitializer = new TenantAdminInitializerImpl(repository, idp, reconciler, organization, clock);
    }

    public IdentityRepository repository() {
        return repository;
    }

    public IdentityProviderPort idp() {
        return idp;
    }

    public AccountReconciler reconciler() {
        return reconciler;
    }

    public PersonIdentityReconciler personReconciler() {
        return personReconciler;
    }

    public SessionService sessions() {
        return sessions;
    }

    public OrganizationService organization() {
        return organization;
    }

    public MembershipService memberships() {
        return memberships;
    }

    public TenantAdminInitializer adminInitializer() {
        return adminInitializer;
    }

    public SmtpHook smtp() {
        return smtp;
    }
}
