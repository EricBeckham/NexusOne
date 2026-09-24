package one.nexus.p2.idp;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.AuditPort;
import one.nexus.common.audit.AuditCategory;
import one.nexus.common.audit.AuditEvent;
import one.nexus.common.trace.TraceIds;
import one.nexus.crypto.SecretPort;
import one.nexus.crypto.SecretRef;
import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.domain.Model;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.session.AdminMfaPolicy;
import one.nexus.p2.session.AdminPrivilege;
import one.nexus.p2.support.DomainException;

/** Encrypted SecretPort operations and connection checks for configured IdPs. */
public final class IdpSecretService {
    public record SecretStatus(boolean configured, Integer version) {}
    public record SecretWriteResult(boolean configured, boolean rotated, int version, Instant updatedAt) {}
    public record ConnectionTestResult(String status, Instant testedAt) {}

    private static final String WRITE_EVENT = "p2.idp.secret.written";
    private static final String ROTATE_EVENT = "p2.idp.secret.rotated";
    private static final String TEST_EVENT = "p2.idp.connection.tested";

    private final IdentityRepository repository;
    private final SecretPort secrets;
    private final AuditPort audits;
    private final Clock clock;
    private final AdminMfaPolicy adminPolicy;
    private final IdpConnectionTester connectionTester;

    public IdpSecretService(
            IdentityRepository repository,
            SecretPort secrets,
            AuditPort audits,
            Clock clock,
            AdminMfaPolicy adminPolicy,
            IdpConnectionTester connectionTester
    ) {
        this.repository = repository;
        this.secrets = secrets;
        this.audits = audits;
        this.clock = clock;
        this.adminPolicy = adminPolicy;
        this.connectionTester = connectionTester;
    }

    public SecretStatus status(UUID providerId) {
        TenantContext context = context();
        Model.IdpConfiguration provider = requireProvider(context.tenantId(), providerId);
        SecretRef ref = requireSecrets().find(secretName(provider)).orElse(null);
        return ref == null ? new SecretStatus(false, null) : new SecretStatus(true, ref.secretVersion());
    }

    public SecretWriteResult write(UUID providerId, String clientSecret) {
        return write(providerId, clientSecret, false);
    }

    public SecretWriteResult rotate(UUID providerId, String clientSecret) {
        return write(providerId, clientSecret, true);
    }

    private SecretWriteResult write(UUID providerId, String clientSecret, boolean requireExisting) {
        TenantContext context = context();
        Model.IdpConfiguration provider = requireProvider(context.tenantId(), providerId);
        byte[] plaintext = validateSecret(clientSecret);
        String name = secretName(provider);
        try {
            SecretPort secretPort = requireSecrets();
            AuditPort auditPort = requireAudit();
            boolean existed = secretPort.find(name).isPresent();
            if (requireExisting && !existed) {
                throw DomainException.notFound("client secret is not configured");
            }
            SecretRef ref;
            try {
                ref = secretPort.put(name, plaintext);
            } catch (RuntimeException ex) {
                throw new DomainException("P2.IAM.IDP_SECRET_WRITE_FAILED", 503, "client secret write failed");
            }
            try {
                audit(auditPort, context, provider, (requireExisting || existed) ? ROTATE_EVENT : WRITE_EVENT,
                        AuditEvent.OUTCOME_ACCEPTED, Map.of("secret_version", Integer.toString(ref.secretVersion())));
            } catch (RuntimeException ex) {
                throw new DomainException("P2.IAM.AUDIT_WRITE_FAILED", 503, "secret audit write failed");
            }
            return new SecretWriteResult(true, existed, ref.secretVersion(), ref.updatedAt());
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    public ConnectionTestResult testConnection(UUID providerId) {
        TenantContext context = context();
        Model.IdpConfiguration provider = requireProvider(context.tenantId(), providerId);
        SecretPort secretPort = requireSecrets();
        AuditPort auditPort = requireAudit();
        String name = secretName(provider);
        if (secretPort.find(name).isEmpty()) {
            throw DomainException.notFound("client secret is not configured");
        }

        byte[] plaintext = null;
        boolean success = false;
        Instant testedAt = clock.instant();
        try {
            plaintext = secretPort.get(name);
            success = connectionTester.test(provider, plaintext);
        } catch (RuntimeException ignored) {
            // Deliberately discard adapter and HTTP diagnostics; they may contain remote response data.
            success = false;
        } finally {
            if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
        }

        if (!repository.recordIdpConnectionTest(context.tenantId(), provider.id(), success, testedAt)) {
            throw DomainException.notFound("identity provider configuration not found");
        }
        audit(auditPort, context, provider, TEST_EVENT,
                success ? AuditEvent.OUTCOME_ACCEPTED : AuditEvent.OUTCOME_REJECTED,
                Map.of("result", success ? "SUCCEEDED" : "FAILED"));
        return new ConnectionTestResult(success ? "SUCCEEDED" : "FAILED", testedAt);
    }

    /** Remove an existing provider secret when N9's metadata soft-delete is invoked. */
    public void deleteSecretIfConfigured(UUID providerId) {
        TenantContext context = context();
        Model.IdpConfiguration provider = requireProvider(context.tenantId(), providerId);
        if (secrets == null) return;
        String name = secretName(provider);
        if (secrets.find(name).isEmpty()) return;
        AuditPort auditPort = requireAudit();
        try {
            secrets.delete(name);
        } catch (RuntimeException ex) {
            throw new DomainException("P2.IAM.IDP_SECRET_DELETE_FAILED", 503, "client secret deletion failed");
        }
        audit(auditPort, context, provider, "p2.idp.secret.deleted", AuditEvent.OUTCOME_ACCEPTED,
                Map.of("operation", "delete"));
    }

    private void audit(
            AuditPort auditPort,
            TenantContext context,
            Model.IdpConfiguration provider,
            String eventType,
            String outcome,
            Map<String, ?> details
    ) {
        auditPort.appendNewTransaction(AuditCaptures.capture(
                outcome, AuditCategory.ADMIN, eventType, context.tenantId(), context.accountId(),
                context.tenantId(), false, eventType, TraceIds.currentOrUnknown(),
                "identity-provider", provider.id().toString(), details));
    }

    private TenantContext context() {
        TenantContext context = TenantContextHolder.require();
        if (!AdminPrivilege.holdsAdminRole(repository, context.accountId(), context.tenantId(), adminPolicy)) {
            throw DomainException.forbidden("tenant administrator role is required");
        }
        return context;
    }

    private Model.IdpConfiguration requireProvider(UUID tenantId, UUID id) {
        return repository.findIdpConfiguration(tenantId, id)
                .orElseThrow(() -> DomainException.notFound("identity provider configuration not found"));
    }

    private SecretPort requireSecrets() {
        if (secrets == null) {
            throw new DomainException("P2.IAM.SECRET_STORE_UNAVAILABLE", 503, "secret store is unavailable");
        }
        return secrets;
    }

    private AuditPort requireAudit() {
        if (audits == null) {
            throw new DomainException("P2.IAM.AUDIT_UNAVAILABLE", 503, "audit service is unavailable");
        }
        return audits;
    }

    private static byte[] validateSecret(String value) {
        if (value == null || value.isBlank() || value.length() > 4096) {
            throw DomainException.badRequest("P2.IAM.IDP_SECRET", "clientSecret is required and must not exceed 4096 characters");
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }

    public static String secretName(Model.IdpConfiguration provider) {
        return "iam.idp." + provider.tenantId() + "." + provider.id() + ".client-secret";
    }
}
