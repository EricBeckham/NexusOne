package one.nexus.p2.idp;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.domain.Model;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.session.AdminMfaPolicy;
import one.nexus.p2.session.AdminPrivilege;
import one.nexus.p2.support.DomainException;

/** Runs the existing tenant reconcile path and stores only sync metadata. */
public final class IdpSyncService {
    public record SyncResult(String status, Instant lastSyncAt, Boolean lastSyncOk, int syncFailCount) {}

    @FunctionalInterface
    public interface DirectorySync {
        void apply(UUID tenantId);
    }

    private static final Set<String> SUPPORTED = Set.of("OIDC", "KEYCLOAK");

    private final IdentityRepository repository;
    private final Clock clock;
    private final AdminMfaPolicy adminPolicy;
    private final DirectorySync directorySync;

    public IdpSyncService(
            IdentityRepository repository,
            Clock clock,
            AdminMfaPolicy adminPolicy,
            DirectorySync directorySync) {
        this.repository = repository;
        this.clock = clock;
        this.adminPolicy = adminPolicy;
        this.directorySync = directorySync;
    }

    public SyncResult sync(UUID id) {
        TenantContext context = context();
        Model.IdpConfiguration provider = repository.findIdpConfiguration(context.tenantId(), id)
                .orElseThrow(() -> DomainException.notFound("identity provider configuration not found"));
        String type = provider.providerType() == null ? "" : provider.providerType().trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED.contains(type)) {
            throw DomainException.badRequest("P2.IAM.IDP_PROVIDER_UNSUPPORTED",
                    "only OIDC and Keycloak providers can be synced");
        }
        boolean succeeded = true;
        try {
            directorySync.apply(context.tenantId());
        } catch (RuntimeException ex) {
            succeeded = false;
        }
        Instant syncedAt = clock.instant();
        if (!repository.recordIdpSync(context.tenantId(), provider.id(), succeeded, syncedAt)) {
            throw DomainException.notFound("identity provider configuration not found");
        }
        Model.IdpConfiguration saved = repository.findIdpConfiguration(context.tenantId(), provider.id()).orElseThrow();
        return new SyncResult(succeeded ? "SUCCEEDED" : "FAILED", saved.lastSyncAt(), saved.lastSyncOk(),
                saved.syncFailCount());
    }

    private TenantContext context() {
        TenantContext context = TenantContextHolder.require();
        if (!AdminPrivilege.holdsAdminRole(repository, context.accountId(), context.tenantId(), adminPolicy)) {
            throw DomainException.forbidden("tenant administrator role is required");
        }
        return context;
    }
}
