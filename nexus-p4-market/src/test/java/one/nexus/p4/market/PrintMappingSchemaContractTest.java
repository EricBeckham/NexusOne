package one.nexus.p4.market;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** DPO-X5: locks L2 schema and docs fixture; not a PostgreSQL migration execution test. */
class PrintMappingSchemaContractTest {
    @Test void fixtureTablesAndColumnsMatchV707() throws Exception {
        var fixture = JsonMapper.builder().build().readTree(Files.readString(Path.of("../docs/planning/fixtures/dpo-print-schema.v1.json")));
        String sql = Files.readString(Path.of("src/main/resources/db/migration/" + fixture.path("migration").asText())).toLowerCase(Locale.ROOT);
        for (String name : new String[]{"market.print_mapping", "market.print_mapping_revision"}) {
            int start = sql.indexOf("create table " + name + " (");
            assertTrue(start >= 0, name);
            String table = sql.substring(start, sql.indexOf(");", start));
            for (var column : fixture.path("tables").path(name)) {
                assertTrue(Pattern.compile("(?m)^\\s*" + Pattern.quote(column.asText()) + "\\s+(uuid|varchar|jsonb|bigint|timestamptz)\\b").matcher(table).find(), name + "." + column.asText());
            }
            assertTrue(table.contains("references market.product_definition(id)"));
            assertTrue(table.contains("check (revision > 0)"));
            assertTrue(table.contains("jsonb_typeof(mappings) = 'array'"));
        }
        assertTrue(sql.contains("primary key (product_definition_id, revision)"));
        assertTrue(sql.contains("on market.print_mapping_revision (product_definition_id, saved_at desc)"));
        assertFalse(fixture.path("outOfOrder").asBoolean(true));
        assertFalse(Flyway.configure().isOutOfOrder());
        try (var configs = Files.walk(Path.of("../nexus-bootstrap/src/main/resources"))) {
            for (Path config : configs.filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml")).toList()) {
                assertFalse(Pattern.compile("(?im)out[-_]?of[-_]?order\\s*:\\s*true").matcher(Files.readString(config)).find(), config.toString());
            }
        }
    }
}
