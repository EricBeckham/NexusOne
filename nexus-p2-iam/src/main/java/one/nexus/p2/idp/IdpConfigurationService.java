package one.nexus.p2.idp;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import one.nexus.common.id.UuidV7;
import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.domain.Model;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.session.AdminMfaPolicy;
import one.nexus.p2.session.AdminPrivilege;
import one.nexus.p2.support.DomainException;

/** CRUD for tenant-scoped IdP metadata; secret material is never accepted here. */
public final class IdpConfigurationService {
    public record Command(String providerType, String displayName, String clientId, String issuer, String status) {}

    private final IdentityRepository repository;
    private final Clock clock;
    private final AdminMfaPolicy adminPolicy;

    public IdpConfigurationService(IdentityRepository repository, Clock clock, AdminMfaPolicy adminPolicy) {
        this.repository = repository;
        this.clock = clock;
        this.adminPolicy = adminPolicy;
    }

    public List<Model.IdpConfiguration> list() {
        return repository.listIdpConfigurations(context().tenantId());
    }

    public Model.IdpConfiguration get(UUID id) {
        return requireProvider(context().tenantId(), id);
    }

    public Model.IdpConfiguration create(Command command) {
        TenantContext context = context();
        validate(command);
        Instant now = clock.instant();
        return repository.saveIdpConfiguration(new Model.IdpConfiguration(
                UuidV7.generate(), context.tenantId(), normalizeProvider(command.providerType()),
                command.displayName().trim(), clean(command.clientId()), cleanIssuer(command.issuer()),
                normalizeStatus(command.status()), null, null, 0, null, now, now, 0));
    }

    public Model.IdpConfiguration update(UUID id, Command command) {
        TenantContext context = context();
        Model.IdpConfiguration existing = requireProvider(context.tenantId(), id);
        validate(command);
        return repository.saveIdpConfiguration(new Model.IdpConfiguration(
                existing.id(), existing.tenantId(), normalizeProvider(command.providerType()),
                command.displayName().trim(), clean(command.clientId()), cleanIssuer(command.issuer()),
                normalizeStatus(command.status()), existing.lastSyncAt(), existing.lastSyncOk(),
                existing.syncFailCount(), existing.deletedAt(), existing.createdAt(), clock.instant(),
                existing.version() + 1));
    }

    public void delete(UUID id) {
        UUID tenantId = context().tenantId();
        requireProvider(tenantId, id);
        if (!repository.deleteIdpConfiguration(tenantId, id)) {
            throw DomainException.notFound("identity provider configuration not found");
        }
    }

    private Model.IdpConfiguration requireProvider(UUID tenantId, UUID id) {
        return repository.findIdpConfiguration(tenantId, id)
                .orElseThrow(() -> DomainException.notFound("identity provider configuration not found"));
    }

    private TenantContext context() {
        TenantContext context = TenantContextHolder.require();
        if (!AdminPrivilege.holdsAdminRole(repository, context.accountId(), context.tenantId(), adminPolicy)) {
            throw DomainException.forbidden("tenant administrator role is required");
        }
        return context;
    }

    private static void validate(Command command) {
        if (command == null) {
            throw DomainException.badRequest("P2.IAM.IDP_CONFIGURATION", "configuration is required");
        }
        normalizeProvider(command.providerType());
        if (command.displayName() == null || command.displayName().isBlank()
                || command.displayName().trim().length() > 256) {
            throw DomainException.badRequest("P2.IAM.IDP_CONFIGURATION", "displayName is required and must be at most 256 characters");
        }
        if (command.clientId() != null && command.clientId().length() > 256) {
            throw DomainException.badRequest("P2.IAM.IDP_CONFIGURATION", "clientId must be at most 256 characters");
        }
        if (command.issuer() != null && !command.issuer().isBlank()) {
            try {
                URI issuer = URI.create(command.issuer().trim());
                if (issuer.getHost() == null || !"https".equalsIgnoreCase(issuer.getScheme())
                        || issuer.getUserInfo() != null || issuer.getQuery() != null || issuer.getFragment() != null) {
                    throw new IllegalArgumentException("issuer must be an absolute HTTPS URL without userinfo/query/fragment");
                }
            } catch (IllegalArgumentException ex) {
                throw DomainException.badRequest("P2.IAM.IDP_CONFIGURATION", "issuer must be an absolute HTTPS URL without userinfo/query/fragment");
            }
        }
        String status = normalizeStatus(command.status());
        if ("ENABLED".equals(status)
                && (command.clientId() == null || command.clientId().isBlank()
                || command.issuer() == null || command.issuer().isBlank())) {
            throw DomainException.badRequest("P2.IAM.IDP_CONFIGURATION", "clientId and issuer are required to enable a provider");
        }
    }

    private static String normalizeProvider(String providerType) {
        String provider = providerType == null ? "" : providerType.trim().toUpperCase(Locale.ROOT);
        if (!"OIDC".equals(provider)) {
            throw DomainException.badRequest("P2.IAM.IDP_PROVIDER_UNSUPPORTED", "only OIDC provider metadata is supported");
        }
        return provider;
    }

    private static String normalizeStatus(String status) {
        String normalized = status == null || status.isBlank()
                ? "DISABLED" : status.trim().toUpperCase(Locale.ROOT);
        if (!"DISABLED".equals(normalized) && !"ENABLED".equals(normalized)) {
            throw DomainException.badRequest("P2.IAM.IDP_CONFIGURATION", "status must be DISABLED or ENABLED");
        }
        return normalized;
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String cleanIssuer(String issuer) {
        String value = clean(issuer);
        if (value == null) return null;
        int end = value.length();
        while (end > 1 && value.charAt(end - 1) == '/') end--;
        return value.substring(0, end);
    }
}
