package one.nexus.p5.connect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class ApiCatalogSchemaContractTest {
    @Test
    void apiCatalogContainsOnlyNonSecretMetadata() throws Exception {
        String sql;
        try (var in = Objects.requireNonNull(
                getClass().getResourceAsStream("/db/migration/V702__p5_open_api_catalog.sql"),
                "migration not on classpath")) {
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        int start = sql.indexOf("CREATE TABLE integration.api_catalog_entry");
        int end = sql.indexOf(";", start);
        assertTrue(start >= 0 && end > start, "API catalog table must exist");
        String table = sql.substring(start, end).toLowerCase();
        for (String column : new String[]{
                "name", "http_method", "path", "version", "scope", "status", "description"
        }) {
            assertTrue(table.matches("(?s).*\\b" + column + "\\b.*"), column + " metadata column must exist");
        }
        for (String forbidden : new String[]{"secret", "credential", "password", "api_key", "token"}) {
            assertFalse(table.contains(forbidden), "API catalog must not store " + forbidden);
        }
        assertTrue(table.contains("unique (http_method, path, version)"),
                "an API version must be unique per method and path");
    }

    @Test
    void tenantScopeIsOptionalAndStillHasNoSecret() throws Exception {
        String sql;
        try (var in = Objects.requireNonNull(
                getClass().getResourceAsStream("/db/migration/V703__p5_api_catalog_tenant.sql"),
                "tenant scope migration not on classpath")) {
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        }
        assertTrue(sql.contains("add column tenant_id uuid null"), "tenant ownership must stay optional");
        for (String forbidden : new String[]{"secret", "credential", "password", "api_key", "token"}) {
            assertFalse(sql.contains(forbidden), "tenant scope migration must not add " + forbidden);
        }
    }
}
