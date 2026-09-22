package one.nexus.p3.flyway;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class FlywaySqlContractTest {
    @Test
    void metadataMigrationsStayInOwnedWindowsAndNeverCreateLedgerTables() throws IOException {
        Path dir = Path.of("src/main/resources/db/migration");
        assertTrue(Files.isDirectory(dir), dir.toAbsolutePath().toString());
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> sql = files.filter(p -> p.getFileName().toString().endsWith(".sql")).toList();
            assertFalse(sql.isEmpty());
            for (Path file : sql) {
                String name = file.getFileName().toString();
                int version = Integer.parseInt(name.substring(1, name.indexOf('_')));
                boolean ledgerWindow = version >= 400 && version <= 499;
                boolean formWindow = version >= 530 && version <= 549;
                boolean flowWindow = version >= 550 && version <= 599;
                assertTrue(
                        ledgerWindow || formWindow || flowWindow,
                        name + " must be V400-V499 (M0-06), V530-V549 (M0-07), or V550-V599 (M0-08)");
                String text = Files.readString(file).toLowerCase(Locale.ROOT);
                assertFalse(text.contains("create table business_data."), file + " must not create business_data tables");
                assertFalse(text.contains("create table l_"), file + " must not create l_* ledger tables");
                assertFalse(text.contains("create table a_"), file + " must not create a_* app ledger tables");
            }
        }
    }
}
