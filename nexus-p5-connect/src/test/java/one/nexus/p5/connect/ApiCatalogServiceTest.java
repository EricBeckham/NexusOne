package one.nexus.p5.connect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ApiCatalogServiceTest {
    private static final UUID TENANT_A = UUID.fromString("01900000-0000-7000-8000-0000000000a1");
    private static final UUID TENANT_B = UUID.fromString("01900000-0000-7000-8000-0000000000b1");
    private static final Instant NOW = Instant.parse("2026-09-24T00:00:00Z");

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void tenantSeesSharedAndOwnEntriesButNotAnotherTenant() {
        UUID sharedId = UUID.randomUUID();
        UUID ownId = UUID.randomUUID();
        UUID foreignId = UUID.randomUUID();
        MemoryCatalog catalog = new MemoryCatalog(List.of(
                entry(sharedId, null, "Shared"),
                entry(ownId, TENANT_A, "Own"),
                entry(foreignId, TENANT_B, "Foreign")));
        ApiCatalogService service = new ApiCatalogService(catalog);
        use(TENANT_A);

        List<UUID> ids = service.list().stream().map(ApiCatalogEntry::id).toList();
        assertEquals(List.of(sharedId, ownId), ids);
        assertEquals(ownId, service.get(ownId).id());
        assertEquals(sharedId, service.get(sharedId).id());
        ResponseStatusException missing = assertThrows(ResponseStatusException.class, () -> service.get(foreignId));
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
    }

    @Test
    void missingTenantContextIsForbidden() {
        ApiCatalogService service = new ApiCatalogService(new MemoryCatalog(List.of()));
        ResponseStatusException denied = assertThrows(ResponseStatusException.class, service::list);
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
    }

    @Test
    void catalogFieldsStayMetadata() {
        List<String> names = new ArrayList<>();
        for (RecordComponent component : ApiCatalogEntry.class.getRecordComponents()) {
            names.add(component.getName().toLowerCase(Locale.ROOT));
        }
        assertTrue(names.containsAll(List.of("name", "httpmethod", "path", "version", "scope", "status")));
        assertTrue(ApiCatalogRepository.VISIBLE_TENANT.contains("tenant_id IS NULL OR tenant_id = :tenant"));
        for (String name : names) {
            assertFalse(name.contains("secret") || name.contains("credential") || name.contains("token")
                    || name.contains("success") || name.contains("call"));
        }
    }

    private static void use(UUID tenantId) {
        var isolation = ConnectIsolationConfiguration.createIsolation(new CollectingAuditSink());
        AccessContextHolder.set(isolation.kernel().tenants().issue(
                new AuthenticatedMembership(
                        Actor.person("catalog-reader"),
                        tenantId.toString(),
                        tenantId.toString(),
                        Set.of(tenantId.toString())),
                "n17-trace"));
    }

    private static ApiCatalogEntry entry(UUID id, UUID tenantId, String name) {
        return new ApiCatalogEntry(id, tenantId, name, "GET", "/api/v1/p5/api-catalog", "v1",
                "integration:read", "ACTIVE", null, NOW, NOW);
    }

    /** Returns every row so the service, not the store, has to hide the other tenant. */
    private static final class MemoryCatalog implements ApiCatalogRepository {
        private final List<ApiCatalogEntry> rows;

        private MemoryCatalog(List<ApiCatalogEntry> rows) {
            this.rows = rows;
        }

        @Override
        public List<ApiCatalogEntry> listVisible(UUID tenantId) {
            return rows;
        }

        @Override
        public Optional<ApiCatalogEntry> findVisible(UUID tenantId, UUID id) {
            return rows.stream().filter(row -> row.id().equals(id)).findFirst();
        }
    }
}
