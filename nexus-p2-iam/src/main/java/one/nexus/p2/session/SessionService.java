package one.nexus.p2.session;

import one.nexus.audit.AuditCaptures;
import one.nexus.audit.AuditPort;
import one.nexus.common.trace.TraceIds;
import one.nexus.p2.account.AccountReconciler;
import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.domain.Model;
import one.nexus.p2.idp.IdentityProviderPort;
import one.nexus.p2.idp.Pkce;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.support.DomainException;
import one.nexus.common.id.UuidV7;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Login / logout / tenant switch. Tenant context is persisted server-side and bound
 * to the IdP subject. Client-supplied tenant_id is never trusted.
 */
public final class SessionService {
    /**
     * DEMO-U6: {@code tenantCode} / {@code tenantName} are display labels only (null when unknown, omitted on the
     * wire); {@code tenantId} stays the value used for switching.
     */
    public record MembershipView(UUID tenantId, UUID personId, Model.MembershipStatus status, String tenantCode,
            String tenantName) {
        public MembershipView(UUID tenantId, UUID personId, Model.MembershipStatus status) {
            this(tenantId, personId, status, null, null);
        }
    }

    public record SessionView(
            UUID accountId,
            String email,
            String displayName,
            UUID contextId,
            UUID tenantId,
            UUID personId,
            List<MembershipView> memberships,
            String accessToken
    ) {}

    public record AuthenticatedIdentity(Model.GlobalAccount account, IdentityProviderPort.IdentityClaims claims) {}

    private final IdentityRepository repository;
    private final AccountReconciler reconciler;
    private final IdentityProviderPort idp;
    private final Clock clock;
    private final Duration contextTtl;
    private final AuditPort audits;
    private final AdminMfaPolicy mfaPolicy;
    private volatile TenantLabelPort tenantLabels = TenantLabelPort.NONE;
    private final LogoutHintStore logoutHints;

    /** DEMO-U6: wire tenant labels (code / name) into membership views; null resets to none. */
    public void useTenantLabels(TenantLabelPort labels) {
        this.tenantLabels = labels == null ? TenantLabelPort.NONE : labels;
    }
    public SessionService(
            IdentityRepository repository,
            AccountReconciler reconciler,
            IdentityProviderPort idp,
            Clock clock,
            Duration contextTtl
    ) {
        this(repository, reconciler, idp, clock, contextTtl, null, AdminMfaPolicy.forced());
    }

    public SessionService(
            IdentityRepository repository,
            AccountReconciler reconciler,
            IdentityProviderPort idp,
            Clock clock,
            Duration contextTtl,
            AuditPort audits
    ) {
        this(repository, reconciler, idp, clock, contextTtl, audits, AdminMfaPolicy.forced());
    }

    public SessionService(
            IdentityRepository repository,
            AccountReconciler reconciler,
            IdentityProviderPort idp,
            Clock clock,
            Duration contextTtl,
            AuditPort audits,
            AdminMfaPolicy mfaPolicy
    ) {
        this.repository = repository;
        this.reconciler = reconciler;
        this.idp = idp;
        this.clock = clock;
        this.contextTtl = contextTtl;
        this.audits = audits;
        this.mfaPolicy = mfaPolicy == null ? AdminMfaPolicy.forced() : mfaPolicy;
        this.logoutHints = new LogoutHintStore(clock);
    }

    /** LO2: server-held IdP logout hints (id_token / refresh_token); never part of a {@link SessionView}. */
    public LogoutHintStore logoutHints() {
        return logoutHints;
    }

    public IdentityProviderPort.AuthorizeUrl authorizationUrl(String state, String redirectUri) {
        return idp.authorizationUrl(state, redirectUri);
    }

    public IdentityProviderPort.AuthorizeUrl authorizationUrl(String state, String redirectUri, String acrValues) {
        return idp.authorizationUrl(state, redirectUri, acrValues);
    }

    public IdentityProviderPort.AuthorizeUrl authorizationUrl(
            String state, String redirectUri, String acrValues, Pkce.Challenge pkce) {
        return idp.authorizationUrl(state, redirectUri, acrValues, pkce);
    }

    public SessionView loginWithCode(String code, String redirectUri) {
        return loginWithCode(code, redirectUri, null);
    }

    public SessionView loginWithCode(String code, String redirectUri, String codeVerifier) {
        IdentityProviderPort.TokenSet tokens = idp.exchangeAuthorizationCode(code, redirectUri, codeVerifier);
        return loginWithAccessToken(tokens.accessToken(), true, tokens);
    }

    public SessionView loginWithAccessToken(String accessToken) {
        // Token-based provisioning can precede membership assignment. Auth-code
        // login, however, must not silently return an unusable tenant session.
        return loginWithAccessToken(accessToken, false, null);
    }

    private SessionView loginWithAccessToken(
            String accessToken, boolean requireActiveMembership, IdentityProviderPort.TokenSet exchanged) {
        boolean accepted = false;
        try {
            IdentityProviderPort.IdentityClaims claims;
            try {
                claims = idp.validate(accessToken);
            } catch (IllegalArgumentException ex) {
                throw DomainException.unauthorized(ex.getMessage());
            }
            Model.GlobalAccount account = reconciler.reconcile(claims);
            if (account.status() != Model.AccountStatus.ACTIVE) {
                throw DomainException.forbidden("account is not active");
            }
            List<MembershipView> memberships = membershipsOf(account.id());
            List<MembershipView> active = memberships.stream()
                    .filter(item -> item.status() == Model.MembershipStatus.ACTIVE)
                    .toList();
            if (requireActiveMembership && active.isEmpty()) {
                throw DomainException.noActiveMembership();
            }
            SessionView view = active.size() == 1
                    ? issue(account, claims, active.getFirst().tenantId(), memberships, accessToken)
                    : new SessionView(
                            account.id(), account.email(), account.displayName(), null, null, null, memberships, accessToken);
            accepted = true;
            if (exchanged != null) {
                // LO2: keep the IdP tokens server-side for RP logout; the SPA only ever gets the access token.
                logoutHints.remember(claims.subject(), exchanged.idToken(), exchanged.refreshToken(),
                        clock.instant().plus(contextTtl));
            }
            appendLogin(true, view.tenantId(), view.accountId(), view.contextId(), "accepted", Map.of(
                    "email", account.email(),
                    "account_id", account.id().toString(),
                    "mfa_satisfied", Boolean.toString(claims.mfaSatisfied())));
            return view;
        } catch (RuntimeException ex) {
            if (!accepted) {
                appendLoginRejected(ex);
            }
            throw ex;
        }
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
        return issue(identity.account(), identity.claims(), requestedTenantId, membershipsOf(identity.account().id()), null);
    }

    public TenantContext loadContext(UUID contextId, AuthenticatedIdentity identity) {
        return loadIssued(contextId, identity.claims().subject(), identity.claims());
    }

    public TenantContext loadContext(UUID contextId, String idpSubject) {
        return loadIssued(contextId, idpSubject, null);
    }

    private TenantContext loadIssued(UUID contextId, String idpSubject, IdentityProviderPort.IdentityClaims claims) {
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
        if (claims != null) {
            requireAdminMfa(stored.accountId(), stored.tenantId(), claims);
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
                membershipsOf(identity.account().id()),
                null);
    }

    /** LO3: {@code endSessionUrl} is empty when there was no IdP hint; the browser may then still hold an SSO session. */
    public record LogoutResult(String endSessionUrl) {
        public boolean hasEndSessionUrl() {
            return endSessionUrl != null && !endSessionUrl.isBlank();
        }
    }

    public void logout(AuthenticatedIdentity identity, String refreshToken, String idTokenHint) {
        logout(identity, refreshToken, idTokenHint, null);
    }

    /**
     * Clears the app session, then best-effort ends the IdP session: back-channel with the server-held hint
     * (failures never block logout), and an RP-initiated logout URL for the browser when a hint and an allowed
     * {@code postLogoutRedirectUri} exist.
     */
    public LogoutResult logout(
            AuthenticatedIdentity identity, String refreshToken, String idTokenHint, String postLogoutRedirectUri) {
        TenantContext current = TenantContextHolder.get();
        // LO2: the session is over, so its IdP hint is spent too (take = read once and drop).
        LogoutHintStore.Hint hint = logoutHints.take(identity.claims().subject()).orElse(null);
        repository.revokeContextsForAccount(identity.account().id());
        TenantContextHolder.clear();
        String refresh = firstNonBlank(refreshToken, hint == null ? null : hint.refreshToken());
        String idToken = firstNonBlank(idTokenHint, hint == null ? null : hint.idToken());
        String backChannel = "skipped";
        if (refresh != null || idToken != null) {
            try {
                idp.logout(refresh, idToken);
                backChannel = "ok";
            } catch (RuntimeException ignored) {
                backChannel = "failed"; // best-effort; the browser redirect below still ends the SSO session
            }
        }
        String endSessionUrl = "";
        if (idToken != null && postLogoutRedirectUri != null && !postLogoutRedirectUri.isBlank()) {
            try {
                endSessionUrl = idp.endSessionUrl(idToken, postLogoutRedirectUri).orElse("");
            } catch (RuntimeException ignored) {
                endSessionUrl = "";
            }
        }
        if (audits != null) {
            audits.append(AuditCaptures.logout(
                    current == null ? null : current.tenantId(),
                    identity.account().id(),
                    current == null || current.contextId() == null
                            ? identity.account().id().toString()
                            : current.contextId().toString(),
                    "logout",
                    TraceIds.currentOrUnknown(),
                    Map.of("account_id", identity.account().id().toString(),
                            "idp_backchannel", backChannel,
                            "end_session", endSessionUrl.isEmpty() ? "absent" : "issued")));
        }
        return new LogoutResult(endSessionUrl);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null || second.isBlank() ? null : second;
    }

    public AuthenticatedIdentity authenticate(String accessToken) {
        IdentityProviderPort.IdentityClaims claims = idp.validate(accessToken);
        Model.GlobalAccount account = repository.findAccountByKeycloakUserId(claims.subject())
                .orElseGet(() -> reconciler.reconcile(claims));
        return new AuthenticatedIdentity(account, claims);
    }

    private SessionView issue(
            Model.GlobalAccount account,
            IdentityProviderPort.IdentityClaims claims,
            UUID tenantId,
            List<MembershipView> memberships,
            String accessToken
    ) {
        requireAdminMfa(account.id(), tenantId, claims);
        Model.Person person = repository.findPersonByAccount(tenantId, account.id())
                .orElseThrow(() -> DomainException.forbidden("person record is missing in the requested tenant"));
        Instant now = clock.instant();
        String subject = claims.subject();
        Model.IssuedContext stored = new Model.IssuedContext(
                UuidV7.generate(), tenantId, account.id(), person.id(), subject, now, now.plus(contextTtl), null);
        repository.saveContext(stored);
        TenantContext context = new TenantContext(
                stored.id(), stored.tenantId(), stored.accountId(), stored.personId(),
                stored.idpSubject(), stored.issuedAt(), stored.expiresAt());
        TenantContextHolder.set(context);
        return new SessionView(
                account.id(), account.email(), account.displayName(),
                stored.id(), tenantId, person.id(), memberships, accessToken);
    }

    private void appendLogin(
            boolean accepted,
            UUID tenantId,
            UUID actorSubjectId,
            UUID resourceId,
            String reason,
            Map<String, ?> details) {
        if (audits == null) {
            return;
        }
        var event = AuditCaptures.login(
                accepted,
                tenantId,
                actorSubjectId,
                resourceId == null ? "session" : resourceId.toString(),
                reason,
                TraceIds.currentOrUnknown(),
                details);
        if (accepted) {
            audits.append(event);
        } else {
            audits.appendNewTransaction(event);
        }
    }

    private void appendLoginRejected(RuntimeException ex) {
        if (audits == null) {
            return;
        }
        try {
            String reason = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? ex.getClass().getSimpleName()
                    : ex.getMessage();
            Map<String, String> details = new LinkedHashMap<>();
            details.put("error", ex.getClass().getSimpleName());
            if (ex instanceof DomainException domain) {
                details.put("code", domain.code());
            }
            appendLogin(false, null, null, null, reason, details);
        } catch (RuntimeException auditEx) {
            ex.addSuppressed(auditEx);
        }
    }

    private void requireAdminMfa(UUID accountId, UUID tenantId, IdentityProviderPort.IdentityClaims claims) {
        if (!mfaPolicy.adminRequired()) {
            return;
        }
        if (!AdminPrivilege.holdsAdminRole(repository, accountId, tenantId, mfaPolicy)) {
            return;
        }
        if (claims != null && claims.mfaSatisfied()) {
            return;
        }
        throw DomainException.mfaRequired(
                "platform or tenant admin privileged session requires MFA");
    }

    private TenantLabelPort.TenantLabel labelOf(UUID tenantId) {
        try {
            return tenantLabels.label(tenantId).orElse(null);
        } catch (RuntimeException unavailable) {
            return null; // labels are cosmetic; never fail a login because a label could not be read
        }
    }

    private List<MembershipView> membershipsOf(UUID accountId) {
        return repository.listMembershipsByAccount(accountId).stream()
                .map(item -> {
                    UUID personId = repository.findPersonByAccount(item.tenantId(), accountId)
                            .map(Model.Person::id)
                            .orElse(null);
                    TenantLabelPort.TenantLabel label = labelOf(item.tenantId());
                    return new MembershipView(item.tenantId(), personId, item.status(),
                            label == null ? null : label.code(), label == null ? null : label.name());
                })
                .toList();
    }
}
