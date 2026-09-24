package one.nexus.p5.connect;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import one.nexus.audit.AuditPort;
import one.nexus.audit.ChainedAuditEvent;
import one.nexus.audit.HashChainVerification;
import one.nexus.common.audit.AuditEvent;
import one.nexus.common.error.NexusException;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import one.nexus.crypto.LocalEncryptedSecretAdapter;
import one.nexus.crypto.SecretPort;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p5.connect.infra.jdbc.JdbcCredentialMetadataRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class CredentialSecretServiceTest {
    private static final UUID TENANT_A = UUID.fromString("01900000-0000-7000-8000-0000000000a1");
    private static final UUID TENANT_B = UUID.fromString("01900000-0000-7000-8000-0000000000b1");
    private static final UUID ACTOR = UUID.fromString("01900000-0000-7000-8000-0000000000aa");
    private static final Instant NOW = Instant.parse("2026-09-24T00:00:00Z");
    private static final String FIRST = "first-credential-value";
    private static final String SECOND = "rotated-credential-value";

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void writeRotateAndRevokeNeverReturnTheSecretAndDropThePreviousValue() throws Exception {
        MemoryCredentials rows = new MemoryCredentials();
        CredentialMetadata row = sample(TENANT_A);
        rows.put(row);
        SecretPort secrets = new LocalEncryptedSecretAdapter(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        MemoryAudit audits = new MemoryAudit();
        CredentialSecretService service = service(rows, secrets, audits);
        use(TENANT_A);

        CredentialSecretService.SecretWriteResult written = service.write(row.id(), FIRST);
        assertEquals(1, written.version());
        assertFalse(written.rotated());
        assertArrayEquals(FIRST.getBytes(StandardCharsets.UTF_8), secrets.get(row.secretName()));
        assertEquals(NOW, rows.find(TENANT_A, row.id()).orElseThrow().lastRotatedAt());

        CredentialSecretService.SecretWriteResult rotated = service.rotate(row.id(), SECOND);
        assertEquals(2, rotated.version());
        assertTrue(rotated.rotated());
        byte[] current = secrets.get(row.secretName());
        assertArrayEquals(SECOND.getBytes(StandardCharsets.UTF_8), current);
        assertFalse(Arrays.equals(FIRST.getBytes(StandardCharsets.UTF_8), current));
        assertEquals("SUCCEEDED", service.probe(row.id()).status());

        CredentialSecretService.SecretStatus revoked = service.revoke(row.id());
        assertEquals("REVOKED", revoked.status());
        assertFalse(revoked.configured());
        assertThrows(NexusException.class, () -> secrets.get(row.secretName()));
        assertEquals("FAILED", service.probe(row.id()).status());
        assertEquals("REVOKED", rows.find(TENANT_A, row.id()).orElseThrow().status());

        ObjectMapper json = new ObjectMapper();
        String encoded = json.writeValueAsString(written) + json.writeValueAsString(rotated) + json.writeValueAsString(revoked);
        assertFalse(encoded.contains(FIRST));
        assertFalse(encoded.contains(SECOND));
        assertFalse(encoded.contains("\"secret\""));
        String audited = audits.events.toString();
        assertFalse(audited.contains(FIRST));
        assertFalse(audited.contains(SECOND));
        assertTrue(audited.contains("p5.credential.secret.rotated"));
    }

    @Test
    void anotherTenantCannotReadOrReplaceTheSecret() {
        MemoryCredentials rows = new MemoryCredentials();
        CredentialMetadata row = sample(TENANT_B);
        rows.put(row);
        SecretPort secrets = new LocalEncryptedSecretAdapter(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        secrets.put(row.secretName(), FIRST.getBytes(StandardCharsets.UTF_8));
        CredentialSecretService service = service(rows, secrets, new MemoryAudit());
        use(TENANT_A);

        ResponseStatusException missing = assertThrows(ResponseStatusException.class, () -> service.rotate(row.id(), SECOND));
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
        assertArrayEquals(FIRST.getBytes(StandardCharsets.UTF_8), secrets.get(row.secretName()));
        assertEquals(CredentialMetadata.ACTIVE, rows.find(TENANT_B, row.id()).orElseThrow().status());
    }

    @Test
    void missingTenantContextIsForbiddenAndRevokedCredentialRejectsANewSecret() {
        MemoryCredentials rows = new MemoryCredentials();
        CredentialMetadata row = sample(TENANT_A);
        rows.put(row);
        SecretPort secrets = new LocalEncryptedSecretAdapter(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        CredentialSecretService service = service(rows, secrets, new MemoryAudit());

        ResponseStatusException denied = assertThrows(ResponseStatusException.class, () -> service.write(row.id(), FIRST));
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertTrue(secrets.find(row.secretName()).isEmpty());

        use(TENANT_A);
        service.write(row.id(), FIRST);
        service.revoke(row.id());
        ResponseStatusException conflict = assertThrows(ResponseStatusException.class, () -> service.write(row.id(), SECOND));
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertThrows(NexusException.class, () -> secrets.get(row.secretName()));
    }

    @Test
    void findSqlStaysOnTheTenantAndDoesNotSelectSecretMaterial() {
        String sql = JdbcCredentialMetadataRepository.FIND_SQL.toLowerCase();
        assertTrue(sql.contains("tenant_id = :tenant"));
        assertTrue(sql.contains("secret_name"));
        for (String forbidden : new String[] {"secret_value", "password", "api_key", "access_token"}) {
            assertFalse(sql.contains(forbidden), forbidden);
        }
    }

    private static CredentialSecretService service(MemoryCredentials rows, SecretPort secrets, MemoryAudit audits) {
        return new CredentialSecretService(rows, secrets, audits, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static void use(UUID tenantId) {
        var isolation = ConnectIsolationConfiguration.createIsolation(new CollectingAuditSink());
        AccessContextHolder.set(isolation.kernel().tenants().issue(
                new AuthenticatedMembership(Actor.person(ACTOR.toString()), tenantId.toString(), tenantId.toString(), Set.of(tenantId.toString())),
                "m17-trace"));
    }

    private static CredentialMetadata sample(UUID tenantId) {
        return new CredentialMetadata(
                UUID.randomUUID(), tenantId, "仓库只读", "minio", CredentialMetadata.ACTIVE,
                null, null, "p5.credential." + tenantId + ".sample", NOW, NOW);
    }

    private static final class MemoryCredentials implements CredentialMetadataRepository {
        private final ConcurrentHashMap<UUID, CredentialMetadata> rows = new ConcurrentHashMap<>();

        void put(CredentialMetadata row) {
            rows.put(row.id(), row);
        }

        @Override
        public List<CredentialMetadata> list(UUID tenantId) {
            return rows.values().stream()
                    .filter(row -> tenantId.equals(row.tenantId()))
                    .sorted(Comparator.comparing(CredentialMetadata::updatedAt).reversed())
                    .toList();
        }

        @Override
        public Optional<CredentialMetadata> findByName(UUID tenantId, String name) {
            return rows.values().stream()
                    .filter(row -> tenantId.equals(row.tenantId()) && name.equals(row.name()))
                    .findFirst();
        }

        @Override
        public void insert(CredentialMetadata row) {
            rows.put(row.id(), row);
        }

        @Override
        public boolean update(UUID tenantId, UUID id, String name, String provider, Instant expiresAt, Instant updatedAt) {
            CredentialMetadata row = find(tenantId, id).orElse(null);
            if (row == null) {
                return false;
            }
            rows.put(id, new CredentialMetadata(
                    row.id(), row.tenantId(), name, provider, row.status(),
                    row.lastRotatedAt(), expiresAt, row.secretName(), row.createdAt(), updatedAt));
            return true;
        }

        @Override
        public Optional<CredentialMetadata> find(UUID tenantId, UUID id) {
            return Optional.ofNullable(rows.get(id)).filter(row -> tenantId.equals(row.tenantId()));
        }

        @Override
        public boolean markRotated(UUID tenantId, UUID id, Instant rotatedAt) {
            CredentialMetadata row = find(tenantId, id).orElse(null);
            if (row == null || !CredentialMetadata.ACTIVE.equals(row.status())) {
                return false;
            }
            rows.put(id, copy(row, row.status(), rotatedAt, rotatedAt));
            return true;
        }

        @Override
        public boolean markRevoked(UUID tenantId, UUID id, Instant revokedAt) {
            CredentialMetadata row = find(tenantId, id).orElse(null);
            if (row == null || !CredentialMetadata.ACTIVE.equals(row.status())) {
                return false;
            }
            rows.put(id, copy(row, CredentialMetadata.REVOKED, row.lastRotatedAt(), revokedAt));
            return true;
        }

        private static CredentialMetadata copy(CredentialMetadata row, String status, Instant rotatedAt, Instant updatedAt) {
            return new CredentialMetadata(
                    row.id(), row.tenantId(), row.name(), row.provider(), status,
                    rotatedAt, row.expiresAt(), row.secretName(), row.createdAt(), updatedAt);
        }
    }

    private static final class MemoryAudit implements AuditPort {
        private final List<AuditEvent> events = new ArrayList<>();

        @Override
        public ChainedAuditEvent append(AuditEvent event) {
            events.add(event);
            return null;
        }

        @Override
        public ChainedAuditEvent appendNewTransaction(AuditEvent event) {
            return append(event);
        }

        @Override
        public List<ChainedAuditEvent> findByTraceId(String traceId) {
            return List.of();
        }

        @Override
        public List<ChainedAuditEvent> findByCorrelationId(String correlationId) {
            return List.of();
        }

        @Override
        public Optional<ChainedAuditEvent> findByEventId(UUID eventId) {
            return Optional.empty();
        }

        @Override
        public List<ChainedAuditEvent> chain() {
            return List.of();
        }

        @Override
        public HashChainVerification verifyChain() {
            throw new UnsupportedOperationException();
        }
    }
}
