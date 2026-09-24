package one.nexus.p4.market;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class MarketProductDefinitionSqlContractTest {
    @Test
    void productDefinitionMigrationPinsPublishedFormAndWorkflowVersions() throws IOException {
        Path migration = Path.of("src/main/resources/db/migration/V601__market_product_definition.sql");
        assertTrue(Files.isRegularFile(migration), migration.toAbsolutePath().toString());
        String sql = Files.readString(migration).toLowerCase(Locale.ROOT);
        assertTrue(sql.contains("create table market.product_definition"));
        assertTrue(sql.contains("form_template_version_id uuid not null references platform.form_template_version(id)"));
        assertTrue(sql.contains("process_definition_key varchar(120)"));
        assertTrue(sql.contains("process_definition_version integer"));
        assertTrue(sql.contains("default_permission_pack jsonb not null"));
        assertTrue(sql.contains("create table market.sku_product_binding"));
        assertTrue(sql.contains("references market.sku(code)"));
        assertTrue(sql.contains("references market.product_definition(id)"));
        assertTrue(sql.contains("product_definition_process_version_pair"));
    }
}
