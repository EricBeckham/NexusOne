package one.nexus.p4.market;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class MarketInstallationProductPinMigrationTest {
    @Test void pinsInstallationToImmutableProductAndFormProcessVersions() throws IOException {
        Path migration = Path.of("src/main/resources/db/migration/V705__market_installation_product_pin.sql");
        String sql = Files.readString(migration).toLowerCase(Locale.ROOT);
        assertTrue(sql.contains("create table market.installation_product_pin"));
        assertTrue(sql.contains("installation_id uuid primary key references market.installation(id)"));
        assertTrue(sql.contains("product_definition_id uuid not null references market.product_definition(id)"));
        assertTrue(sql.contains("form_template_version_id uuid not null references platform.form_template_version(id)"));
        assertTrue(sql.contains("process_definition_key varchar(120)"));
        assertTrue(sql.contains("process_definition_version integer"));
        assertTrue(sql.contains("installation_product_pin_process_version_pair"));
    }
}
