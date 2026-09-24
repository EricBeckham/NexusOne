package one.nexus.p5.connect;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.context.AccessContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Metadata CRUD. Secret material stays on the secret subpaths and never enters these records. */
@Service
public class CredentialMetadataService {
    private static final int MAX_NAME = 160;
    private static final int MAX_PROVIDER = 100;

    private final CredentialMetadataRepository repository;
    private final Clock clock;

    @Autowired
    public CredentialMetadataService(CredentialMetadataRepository repository) {
        this(repository, Clock.systemUTC());
    }

    CredentialMetadataService(CredentialMetadataRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public List<CredentialMetadata> list() {
        return repository.list(tenantId());
    }

    public CredentialMetadata get(UUID id) {
        return require(tenantId(), id);
    }

    public CredentialMetadata create(String name, String provider, Instant expiresAt) {
        UUID tenantId = tenantId();
        String checkedName = required(name, MAX_NAME, "name");
        String checkedProvider = required(provider, MAX_PROVIDER, "provider");
        if (repository.findByName(tenantId, checkedName).isPresent()) {
            throw conflict();
        }
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        CredentialMetadata row = new CredentialMetadata(
                id,
                tenantId,
                checkedName,
                checkedProvider,
                CredentialMetadata.ACTIVE,
                null,
                expiresAt,
                "p5.credential." + tenantId + "." + id,
                now,
                now);
        try {
            repository.insert(row);
        } catch (DataIntegrityViolationException ex) {
            throw conflict();
        }
        return row;
    }

    public CredentialMetadata update(UUID id, String name, String provider, Instant expiresAt) {
        UUID tenantId = tenantId();
        CredentialMetadata current = require(tenantId, id);
        String checkedName = required(name, MAX_NAME, "name");
        String checkedProvider = required(provider, MAX_PROVIDER, "provider");
        repository.findByName(tenantId, checkedName)
                .filter(other -> !other.id().equals(id))
                .ifPresent(other -> {
                    throw conflict();
                });
        Instant now = clock.instant();
        try {
            if (!repository.update(tenantId, id, checkedName, checkedProvider, expiresAt, now)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "credential not found");
            }
        } catch (DataIntegrityViolationException ex) {
            throw conflict();
        }
        return new CredentialMetadata(
                current.id(),
                current.tenantId(),
                checkedName,
                checkedProvider,
                current.status(),
                current.lastRotatedAt(),
                expiresAt,
                current.secretName(),
                current.createdAt(),
                now);
    }

    private CredentialMetadata require(UUID tenantId, UUID id) {
        return repository.find(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "credential not found"));
    }

    private UUID tenantId() {
        AccessContext context = AccessContextHolder.currentOrNull();
        if (!(context instanceof TenantContext tenant)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TenantContext is required");
        }
        try {
            return UUID.fromString(tenant.tenantId());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TenantContext has an invalid tenant id");
        }
    }

    private static String required(String value, int max, String field) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required and must not exceed " + max + " characters");
        }
        return value.trim();
    }

    private static ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "credential name already exists");
    }
}
