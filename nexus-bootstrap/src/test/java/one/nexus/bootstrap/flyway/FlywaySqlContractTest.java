package one.nexus.bootstrap.flyway;

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
    void staticMigrationsDoNotCreateLedgerTables() throws IOException {
        Path dir = Path.of("src/main/resources/db/migration");
        assertTrue(Files.isDirectory(dir), dir.toAbsolutePath().toString());
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> sql = files.filter(p -> p.getFileName().toString().endsWith(".sql")).toList();
            assertFalse(sql.isEmpty());
            for (Path file : sql) {
                String text = Files.readString(file).toLowerCase(Locale.ROOT);
                assertFalse(text.contains("create table business_data."), file + " must not create business_data tables");
                assertFalse(text.contains("create table l_"), file + " must not create l_* ledger tables");
                assertFalse(text.contains("create table a_"), file + " must not create a_* app ledger tables");
            }
        }
    }

    @Test
    void versionWindowIsFoundationOnly() throws IOException {
        Path dir = Path.of("src/main/resources/db/migration");
        try (Stream<Path> files = Files.list(dir)) {
            files.map(p -> p.getFileName().toString())
                    .filter(name -> name.startsWith("V"))
                    .forEach(name -> {
                        int version = Integer.parseInt(name.substring(1, name.indexOf('_')));
                        assertTrue(version >= 1 && version <= 99, name + " is reserved 001-099 for M0-01");
                    });
        }
    }

    @Test
    void secretCiphertextMigrationStoresCiphertextAndNoPlaintextSecretColumns() throws IOException {
        Path migration = Path.of("src/main/resources/db/migration/V004__platform_secret_ciphertext.sql");
        assertTrue(Files.isRegularFile(migration), migration.toAbsolutePath().toString());
        String sql = Files.readString(migration).toLowerCase(Locale.ROOT);
        int start = sql.indexOf("create table platform.secret_ciphertext");
        int end = sql.indexOf(";", start);
        assertTrue(start >= 0 && end > start, "SecretPort ciphertext table must exist");
        String table = sql.substring(start, end);
        assertTrue(table.contains("secret_name varchar(512) primary key"));
        assertTrue(table.contains("version integer not null"));
        assertTrue(table.contains("ciphertext bytea not null"));
        assertTrue(table.contains("key_id varchar(128) not null"));
        assertTrue(table.contains("updated_at timestamptz not null"));
        for (String forbidden : new String[]{"plaintext", "password", "client_secret", "secret_value"}) {
            assertFalse(table.contains(forbidden), "secret ciphertext table must not store " + forbidden);
        }
    }
}
