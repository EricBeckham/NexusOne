package one.nexus.p5.connect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

class CredentialMetadataServiceTest {
    private static final UUID TENANT_A = UUID.fromString("01900000-0000-7000-8000-0000000000a1");
    private static final UUID TENANT_B = UUID.fromString("01900000-0000-7000-8000-0000000000b1");
    private static final UUID ACTOR = UUID.fromString("01900000-0000-7000-8000-0000000000aa");
    private static final Instant NOW = Instant.parse("2026-09-25T00:00:00Z");
    private static final Instant LATER = Instant.parse("2026-12-01T00:00:00Z");

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void createListGetAndUpdateStayOnMetadata() throws Exception {
        MemoryCredentials rows = new MemoryCredentials();
        CredentialMetadataService service = new CredentialMetadataService(rows, Clock.fixed(NOW, ZoneOffset.UTC));
        use(TENANT_A);

        CredentialMetadata created = service.create("仓库只读", "minio", LATER);
        assertEquals(CredentialMetadata.ACTIVE, created.status());
        assertTrue(created.secretName().startsWith("p5.credential." + TENANT_A + "."));
        assertFalse(created.secretName().contains("仓库只读"));
        assertEquals(List.of(created.id()), service.list().stream().map(CredentialMetadata::id).toList());

        CredentialMetadata updated = service.update(created.id(), "仓库读写", "s3", null);
        assertEquals("仓库读写", updated.name());
        assertEquals("s3", updated.provider());
        assertEquals(created.secretName(), updated.secretName());
        assertEquals(CredentialMetadata.ACTIVE, updated.status());
        assertEquals(null, updated.expiresAt());

        String json = new ObjectMapper().writeValueAsString(updated);
        assertFalse(json.contains("plaintext"));
        assertFalse(json.contains("password"));
        assertTrue(json.contains(created.secretName()));
    }

    @Test
    void anotherTenantCannotSeeOrRenameTheCredential() {
        MemoryCredentials rows = new MemoryCredentials();
        CredentialMetadataService service = new CredentialMetadataService(rows, Clock.fixed(NOW, ZoneOffset.UTC));
        use(TENANT_A);
        CredentialMetadata created = service.create("本租户", "minio", null);

        use(TENANT_B);
        assertTrue(service.list().isEmpty());
        ResponseStatusException missing = assertThrows(ResponseStatusException.class, () -> service.get(created.id()));
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
        ResponseStatusException rename = assertThrows(ResponseStatusException.class, () -> service.update(created.id(), "别的名字", "s3", null));
        assertEquals(HttpStatus.NOT_FOUND, rename.getStatusCode());
        assertEquals("本租户", rows.find(TENANT_A, created.id()).orElseThrow().name());
    }

    @Test
    void duplicateNameConflictsAndMissingContextIsForbidden() {
        MemoryCredentials rows = new MemoryCredentials();
        CredentialMetadataService service = new CredentialMetadataService(rows, Clock.fixed(NOW, ZoneOffset.UTC));
        ResponseStatusException denied = assertThrows(ResponseStatusException.class, () -> service.create("仓库只读", "minio", null));
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());

        use(TENANT_A);
        service.create("仓库只读", "minio", null);
        ResponseStatusException conflict = assertThrows(ResponseStatusException.class, () -> service.create("仓库只读", "s3", null));
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals(1, service.list().size());
    }

    @Test
    void metadataRecordAndListQueryDoNotCarrySecretMaterial() {
        List<String> names = new ArrayList<>();
        for (RecordComponent component : CredentialMetadata.class.getRecordComponents()) {
            names.add(component.getName().toLowerCase(Locale.ROOT));
        }
        assertTrue(names.contains("secretname"));
        for (String forbidden : List.of("password", "token", "plaintext", "secretvalue", "apikey")) {
            assertFalse(names.contains(forbidden), forbidden);
        }
        String sql = (JdbcCredentialMetadataRepository.LIST_SQL + JdbcCredentialMetadataRepository.FIND_SQL).toLowerCase(Locale.ROOT);
        assertTrue(sql.contains("tenant_id = :tenant"));
        for (String forbidden : List.of("secret_value", "password", "api_key", "access_token")) {
            assertFalse(sql.contains(forbidden), forbidden);
        }
    }

    private static void use(UUID tenantId) {
        var isolation = ConnectIsolationConfiguration.createIsolation(new CollectingAuditSink());
        AccessContextHolder.set(isolation.kernel().tenants().issue(
                new AuthenticatedMembership(Actor.person(ACTOR.toString()), tenantId.toString(), tenantId.toString(), Set.of(tenantId.toString())),
                "m18-trace"));
    }

    private static final class MemoryCredentials implements CredentialMetadataRepository {
        private final ConcurrentHashMap<UUID, CredentialMetadata> rows = new ConcurrentHashMap<>();

        @Override
        public List<CredentialMetadata> list(UUID tenantId) {
            return rows.values().stream()
                    .filter(row -> tenantId.equals(row.tenantId()))
                    .sorted(Comparator.comparing(CredentialMetadata::updatedAt).reversed())
                    .toList();
        }

        @Override
        public Optional<CredentialMetadata> find(UUID tenantId, UUID id) {
            return Optional.ofNullable(rows.get(id)).filter(row -> tenantId.equals(row.tenantId()));
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
        public boolean markRotated(UUID tenantId, UUID id, Instant rotatedAt) {
            return false;
        }

        @Override
        public boolean markRevoked(UUID tenantId, UUID id, Instant revokedAt) {
            return false;
        }
    }
}
