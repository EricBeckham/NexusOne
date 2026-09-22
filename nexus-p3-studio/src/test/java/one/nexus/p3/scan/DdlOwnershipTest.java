package one.nexus.p3.scan;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DdlOwnershipTest {
    @Test
    void onlySchemaServiceExecutesDynamicDdl() throws IOException {
        Path root = Path.of("src/main/java/one/nexus/p3");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                String relative = path.toString().replace('\\', '/');
                if (relative.contains("/infra/schema/JdbcSchemaService.java")
                        || relative.contains("/infra/schema/PostgresDialectRenderer.java")) {
                    return;
                }
                String text;
                try {
                    text = Files.readString(path, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
                if (text.contains("create table ")
                        || text.contains("alter table ")
                        || text.contains("drop table ")
                        || text.contains("create index ")) {
                    offenders.add(relative);
                }
            });
        }
        assertTrue(offenders.isEmpty(), "dynamic DDL leaked outside SchemaService: " + offenders);
    }
}
