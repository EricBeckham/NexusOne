package one.nexus.p5.connect;

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
import one.nexus.crypto.SecretPort;
import one.nexus.crypto.SecretRef;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.context.AccessContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Writes, rotates, and revokes a credential secret through {@link SecretPort}. Responses never carry plaintext. */
@Service
public class CredentialSecretService {
    public record SecretStatus(boolean configured, Integer version, String status) {}

    public record SecretWriteResult(boolean configured, boolean rotated, int version, Instant updatedAt) {}

    public record SecretProbe(String status) {}

    private static final String WRITTEN = "p5.credential.secret.written";
    private static final String ROTATED = "p5.credential.secret.rotated";
    private static final String REVOKED = "p5.credential.secret.revoked";
    private static final String PROBED = "p5.credential.secret.probed";
    private static final int MAX_SECRET_CHARS = 4096;

    private final CredentialMetadataRepository repository;
    private final SecretPort secrets;
    private final AuditPort audits;
    private final Clock clock;

    @Autowired
    public CredentialSecretService(CredentialMetadataRepository repository, SecretPort secrets, AuditPort audits) {
        this(repository, secrets, audits, Clock.systemUTC());
    }

    CredentialSecretService(
            CredentialMetadataRepository repository, SecretPort secrets, AuditPort audits, Clock clock) {
        this.repository = repository;
        this.secrets = secrets;
        this.audits = audits;
        this.clock = clock;
    }

    public SecretStatus status(UUID id) {
        TenantScope scope = scope();
        CredentialMetadata row = require(scope.tenantId(), id);
        SecretRef ref = requireSecrets().find(row.secretName()).orElse(null);
        return new SecretStatus(ref != null, ref == null ? null : ref.secretVersion(), row.status());
    }

    public SecretWriteResult write(UUID id, String secret) {
        return store(id, secret, false);
    }

    public SecretWriteResult rotate(UUID id, String secret) {
        return store(id, secret, true);
    }

    public SecretStatus revoke(UUID id) {
        TenantScope scope = scope();
        CredentialMetadata row = require(scope.tenantId(), id);
        SecretPort secretPort = requireSecrets();
        AuditPort auditPort = requireAudits();
        try {
            secretPort.delete(row.secretName());
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "credential secret deletion failed");
        }
        Instant now = clock.instant();
        if (CredentialMetadata.ACTIVE.equals(row.status()) && !repository.markRevoked(scope.tenantId(), id, now)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "credential not found");
        }
        audit(auditPort, scope, id, REVOKED, AuditEvent.OUTCOME_ACCEPTED, Map.of("operation", "revoke", "reference", row.secretName()));
        return new SecretStatus(false, null, CredentialMetadata.REVOKED);
    }

    /** Confirms the stored value can be read. The bytes are discarded and never returned. */
    public SecretProbe probe(UUID id) {
        TenantScope scope = scope();
        CredentialMetadata row = require(scope.tenantId(), id);
        SecretPort secretPort = requireSecrets();
        AuditPort auditPort = requireAudits();
        boolean readable = false;
        if (CredentialMetadata.ACTIVE.equals(row.status()) && secretPort.find(row.secretName()).isPresent()) {
            byte[] plaintext = null;
            try {
                plaintext = secretPort.get(row.secretName());
                readable = plaintext != null && plaintext.length > 0;
            } catch (RuntimeException ignored) {
                readable = false;
            } finally {
                if (plaintext != null) {
                    Arrays.fill(plaintext, (byte) 0);
                }
            }
        }
        String status = readable ? "SUCCEEDED" : "FAILED";
        audit(auditPort, scope, id, PROBED, readable ? AuditEvent.OUTCOME_ACCEPTED : AuditEvent.OUTCOME_REJECTED,
                Map.of("result", status, "reference", row.secretName()));
        return new SecretProbe(status);
    }

    private SecretWriteResult store(UUID id, String secret, boolean replace) {
        TenantScope scope = scope();
        CredentialMetadata row = require(scope.tenantId(), id);
        if (!CredentialMetadata.ACTIVE.equals(row.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "revoked credential cannot store a secret");
        }
        byte[] plaintext = validate(secret);
        String reference = row.secretName();
        try {
            SecretPort secretPort = requireSecrets();
            AuditPort auditPort = requireAudits();
            boolean existed = secretPort.find(reference).isPresent();
            if (replace && !existed) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "credential secret is not configured");
            }
            if (!replace && existed) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "credential secret already exists; rotate it");
            }
            SecretRef ref;
            try {
                ref = secretPort.put(reference, plaintext);
            } catch (RuntimeException ex) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "credential secret write failed");
            }
            Instant now = clock.instant();
            if (!repository.markRotated(scope.tenantId(), id, now)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "credential is no longer active");
            }
            audit(auditPort, scope, id, replace ? ROTATED : WRITTEN, AuditEvent.OUTCOME_ACCEPTED,
                    Map.of("version", Integer.toString(ref.secretVersion()), "reference", reference));
            return new SecretWriteResult(true, replace, ref.secretVersion(), ref.updatedAt());
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    private void audit(
            AuditPort auditPort, TenantScope scope, UUID id, String eventType, String outcome, Map<String, ?> details) {
        try {
            auditPort.appendNewTransaction(AuditCaptures.capture(
                    outcome,
                    AuditCategory.ADMIN,
                    eventType,
                    scope.tenantId(),
                    scope.actorId(),
                    scope.tenantId(),
                    false,
                    eventType,
                    scope.traceId(),
                    "credential",
                    id.toString(),
                    details));
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "credential secret audit write failed");
        }
    }

    private CredentialMetadata require(UUID tenantId, UUID id) {
        return repository.find(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "credential not found"));
    }

    private SecretPort requireSecrets() {
        if (secrets == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "secret store is unavailable");
        }
        return secrets;
    }

    private AuditPort requireAudits() {
        if (audits == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "audit service is unavailable");
        }
        return audits;
    }

    private static byte[] validate(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_SECRET_CHARS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "secret is required and must not exceed 4096 characters");
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private TenantScope scope() {
        AccessContext context = AccessContextHolder.currentOrNull();
        if (!(context instanceof TenantContext tenant)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TenantContext is required");
        }
        UUID tenantId;
        try {
            tenantId = UUID.fromString(tenant.tenantId());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TenantContext has an invalid tenant id");
        }
        UUID actorId = null;
        try {
            actorId = UUID.fromString(tenant.actor().actorId());
        } catch (IllegalArgumentException ignored) {
            actorId = null;
        }
        return new TenantScope(tenantId, actorId, tenant.traceId());
    }

    private record TenantScope(UUID tenantId, UUID actorId, String traceId) {}
}
