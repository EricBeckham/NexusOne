package one.nexus.p3.flow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class NoFlowableInternalTableScanTest {
    @Test
    void productCodeDoesNotQueryFlowableActTables() throws Exception {
        Path root = Path.of("src/main/java");
        assertTrue(Files.isDirectory(root), root.toAbsolutePath().toString());
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                String text;
                try {
                    text = Files.readString(path, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                } catch (Exception ex) {
                    throw new IllegalStateException(path.toString(), ex);
                }
                assertFalse(text.contains("from act_"), path + " must not SELECT Flowable act_* tables");
                assertFalse(text.contains("join act_"), path + " must not JOIN Flowable act_* tables");
                assertFalse(text.contains("into act_"), path + " must not write Flowable act_* tables");
            });
        }
    }

    @Test
    void flywayWindowIsV550ToV599AndDoesNotCreateActTables() throws Exception {
        Path dir = Path.of("src/main/resources/db/migration");
        assertTrue(Files.isDirectory(dir));
        try (Stream<Path> files = Files.list(dir)) {
            files.map(Path::getFileName).map(Path::toString).filter(name -> name.startsWith("V")).forEach(name -> {
                int version = Integer.parseInt(name.substring(1, name.indexOf('_')));
                boolean ledgerWindow = version >= 400 && version <= 499;
                boolean formWindow = version >= 530 && version <= 549;
                boolean flowWindow = version >= 550 && version <= 599;
                assertTrue(
                        ledgerWindow || formWindow || flowWindow,
                        name + " must be V400-V499 (M0-06), V530-V549 (M0-07), or V550-V599 (M0-08)");
            });
        }
        String sql = Files.readString(dir.resolve("V550__process_metadata.sql"), StandardCharsets.UTF_8)
                .toLowerCase(Locale.ROOT);
        assertFalse(sql.contains("create table act_") || sql.contains("create table flowable.act"));
        assertTrue(sql.contains("create schema if not exists flowable"));
        assertTrue(sql.contains("business_meta.process_instance"));
        assertTrue(sql.contains("business_meta.process_todo"));
    }
}
