package one.nexus.p2.session;

import one.nexus.p2.account.AccountReconciler;
import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.domain.Model;
import one.nexus.p2.idp.IdentityProviderPort;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.support.DomainException;
import one.nexus.common.id.UuidV7;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Login / logout / tenant switch. Tenant context is persisted server-side and bound
 * to the IdP subject. Client-supplied tenant_id is never trusted.
 */
public final class SessionService {
    public record MembershipView(UUID tenantId, UUID personId, Model.MembershipStatus status) {}

    public record SessionView(
            UUID accountId,
            String email,
            String displayName,
            UUID contextId,
            UUID tenantId,
            UUID personId,
            List<MembershipView> memberships
    ) {}

    public record AuthenticatedIdentity(Model.GlobalAccount account, IdentityProviderPort.IdentityClaims claims) {}

    private final IdentityRepository repository;
    private final AccountReconciler reconciler;
    private final IdentityProviderPort idp;
    private final Clock clock;
    private final Duration contextTtl;

    public SessionService(
            IdentityRepository repository,
            AccountReconciler reconciler,
            IdentityProviderPort idp,
            Clock clock,
            Duration contextTtl
    ) {
        this.repository = repository;
        this.reconciler = reconciler;
        this.idp = idp;
        this.clock = clock;
        this.contextTtl = contextTtl;
    }

    public IdentityProviderPort.AuthorizeUrl authorizationUrl(String state, String redirectUri) {
        return idp.authorizationUrl(state, redirectUri);
    }

    public SessionView loginWithCode(String code, String redirectUri) {
        IdentityProviderPort.TokenSet tokens = idp.exchangeAuthorizationCode(code, redirectUri);
        return loginWithAccessToken(tokens.accessToken());
    }

    public SessionView loginWithAccessToken(String accessToken) {
        IdentityProviderPort.IdentityClaims claims = idp.validate(accessToken);
        Model.GlobalAccount account = reconciler.reconcile(claims);
        if (account.status() != Model.AccountStatus.ACTIVE) {
            throw DomainException.forbidden("account is not active");
        }
        List<MembershipView> memberships = membershipsOf(account.id());
        List<MembershipView> active = memberships.stream()
                .filter(item -> item.status() == Model.MembershipStatus.ACTIVE)
                .toList();
        if (active.size() == 1) {
            return issue(account, claims.subject(), active.getFirst().tenantId(), memberships);
        }
        return new SessionView(account.id(), account.email(), account.displayName(), null, null, null, memberships);
    }

    public SessionView switchTenant(AuthenticatedIdentity identity, UUID requestedTenantId) {
        if (requestedTenantId == null) {
            throw DomainException.badRequest("P2.IAM.TENANT_REQUIRED", "tenantId is required for switch");
        }
        Model.TenantMembership membership = repository.findMembership(requestedTenantId, identity.account().id())
                .orElseThrow(() -> DomainException.forbidden("not a member of the requested tenant"));
        if (membership.status() != Model.MembershipStatus.ACTIVE) {
            throw DomainException.forbidden("tenant membership is not active");
        }
        return issue(identity.account(), identity.claims().subject(), requestedTenantId, membershipsOf(identity.account().id()));
    }

    public TenantContext loadContext(UUID contextId, String idpSubject) {
        Model.IssuedContext stored = repository.findContext(contextId)
                .orElseThrow(() -> DomainException.unauthorized("tenant context is not issued"));
        if (stored.revokedAt() != null) {
            throw DomainException.unauthorized("tenant context is revoked");
        }
        Instant now = clock.instant();
        if (!now.isBefore(stored.expiresAt())) {
            throw DomainException.unauthorized("tenant context expired");
        }
        if (!stored.idpSubject().equals(idpSubject)) {
            throw DomainException.forbidden("tenant context does not belong to this identity");
        }
        TenantContext context = new TenantContext(
                stored.id(), stored.tenantId(), stored.accountId(), stored.personId(),
                stored.idpSubject(), stored.issuedAt(), stored.expiresAt());
        TenantContextHolder.set(context);
        return context;
    }

    public SessionView current(AuthenticatedIdentity identity) {
        TenantContext context = TenantContextHolder.get();
        return new SessionView(
                identity.account().id(),
                identity.account().email(),
                identity.account().displayName(),
                context == null ? null : context.contextId(),
                context == null ? null : context.tenantId(),
                context == null ? null : context.personId(),
                membershipsOf(identity.account().id()));
    }

    public void logout(AuthenticatedIdentity identity, String refreshToken, String idTokenHint) {
        repository.revokeContextsForAccount(identity.account().id());
        TenantContextHolder.clear();
        idp.logout(refreshToken, idTokenHint);
    }

    public AuthenticatedIdentity authenticate(String accessToken) {
        IdentityProviderPort.IdentityClaims claims = idp.validate(accessToken);
        Model.GlobalAccount account = repository.findAccountByKeycloakUserId(claims.subject())
                .orElseGet(() -> reconciler.reconcile(claims));
        return new AuthenticatedIdentity(account, claims);
    }

    private SessionView issue(Model.GlobalAccount account, String subject, UUID tenantId, List<MembershipView> memberships) {
        Model.Person person = repository.findPersonByAccount(tenantId, account.id())
                .orElseThrow(() -> DomainException.forbidden("person record is missing in the requested tenant"));
        Instant now = clock.instant();
        Model.IssuedContext stored = new Model.IssuedContext(
                UuidV7.generate(), tenantId, account.id(), person.id(), subject, now, now.plus(contextTtl), null);
        repository.saveContext(stored);
        TenantContext context = new TenantContext(
                stored.id(), stored.tenantId(), stored.accountId(), stored.personId(),
                stored.idpSubject(), stored.issuedAt(), stored.expiresAt());
        TenantContextHolder.set(context);
        return new SessionView(
                account.id(), account.email(), account.displayName(),
                stored.id(), tenantId, person.id(), memberships);
    }

    private List<MembershipView> membershipsOf(UUID accountId) {
        return repository.listMembershipsByAccount(accountId).stream()
                .map(item -> {
                    UUID personId = repository.findPersonByAccount(item.tenantId(), accountId)
                            .map(Model.Person::id)
                            .orElse(null);
                    return new MembershipView(item.tenantId(), personId, item.status());
                })
                .toList();
    }
}
