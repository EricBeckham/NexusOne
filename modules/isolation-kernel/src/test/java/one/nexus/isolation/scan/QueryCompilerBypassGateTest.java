package one.nexus.isolation.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * G1 leftover gate: an intentional QueryCompiler bypass fails; product
 * {@code src/main} with the documented allowlist passes.
 */
class QueryCompilerBypassGateTest {
    private final QueryCompilerBypassGate gate = new QueryCompilerBypassGate();
    private final Path fixtures = Path.of("src/test/resources/querycompiler-bypass-fixtures");

    @Test
    void intentionalTenantTableJdbcFailsTheGate() throws Exception {
        List<QueryCompilerBypassGate.Violation> violations =
                gate.scan(fixtures.resolve("negative/tenant-jdbc"), false);
        assertFalse(violations.isEmpty(), "expected the l_demo_leave JdbcTemplate caller to fail");
        assertTrue(
                violations.stream().anyMatch(v -> QueryCompilerBypassGate.TENANT_TABLE_SQL.equals(v.rule())),
                violations::toString);
        assertTrue(
                violations.stream().anyMatch(v -> QueryCompilerBypassGate.RAW_JDBC.equals(v.rule())),
                violations::toString);
    }

    @Test
    void intentionalMyBatisTenantMapperFailsTheGate() throws Exception {
        List<QueryCompilerBypassGate.Violation> violations =
                gate.scan(fixtures.resolve("negative/mybatis"), false);
        assertFalse(violations.isEmpty(), "expected MyBatis ${} on l_demo_leave to fail");
        assertTrue(
                violations.stream().anyMatch(v -> QueryCompilerBypassGate.TENANT_TABLE_SQL.equals(v.rule())),
                violations::toString);
        assertTrue(
                violations.stream().anyMatch(v -> QueryCompilerBypassGate.RAW_JDBC.equals(v.rule())),
                violations::toString);
    }

    @Test
    void intentionalApiLayerJdbcFailsTheGate() throws Exception {
        List<QueryCompilerBypassGate.Violation> violations =
                gate.scan(fixtures.resolve("negative/layer"), false);
        assertEquals(1, violations.size(), violations::toString);
        assertEquals(QueryCompilerBypassGate.RAW_JDBC, violations.getFirst().rule());
        assertTrue(violations.getFirst().file().getFileName().toString().equals("Controller.java"));
    }

    @Test
    void cleanBusinessFixturePasses() throws Exception {
        List<QueryCompilerBypassGate.Violation> violations =
                gate.scan(fixtures.resolve("positive/clean"), false);
        assertTrue(violations.isEmpty(), violations::toString);
    }

    @Test
    void allowlistAcceptsCatalogJdbcAndRejectsMarketTenantDump() throws Exception {
        List<QueryCompilerBypassGate.Violation> violations =
                gate.scan(fixtures.resolve("mini-product"), true);
        assertFalse(violations.isEmpty(), "mini-product RawTenantDump must fail");
        assertTrue(
                violations.stream()
                        .allMatch(v -> v.file().getFileName().toString().equals("RawTenantDump.java")),
                violations::toString);
        assertTrue(
                violations.stream().anyMatch(v -> QueryCompilerBypassGate.TENANT_TABLE_SQL.equals(v.rule())),
                violations::toString);
        assertTrue(
                violations.stream().anyMatch(v -> QueryCompilerBypassGate.RAW_JDBC.equals(v.rule())),
                violations::toString);
        assertTrue(
                violations.stream().noneMatch(v -> v.file().getFileName().toString().equals("CatalogOnly.java")),
                violations::toString);
    }

    @Test
    void productMainPassesWithDocumentedAllowlist() throws Exception {
        Path repo = QueryCompilerBypassGate.findRepoRoot(Path.of(System.getProperty("user.dir")));
        List<QueryCompilerBypassGate.Violation> violations = gate.scanProduct(repo);
        assertTrue(violations.isEmpty(), () -> "product src/main must pass:\n" + format(violations));
    }

    @Test
    void allowlistDocumentsEveryJdbcModule() {
        assertFalse(QueryCompilerBypassAllowlist.MODULES.isEmpty());
        assertTrue(
                QueryCompilerBypassAllowlist.jdbcPathPrefixes().stream()
                        .anyMatch(p -> p.startsWith("modules/isolation-kernel/")));
        assertTrue(QueryCompilerBypassAllowlist.TENANT_TABLE_SQL_PATHS.stream()
                .anyMatch(p -> p.endsWith("GuardedJdbc.java")));
        assertTrue(QueryCompilerBypassAllowlist.TENANT_TABLE_SQL_PATHS.stream()
                .anyMatch(p -> p.endsWith("GuardedLedgerStore.java")));
        assertFalse(
                QueryCompilerBypassAllowlist.PRODUCT_SOURCE_ROOTS.contains("poc"),
                "poc is not a product source root");
    }

    private static String format(List<QueryCompilerBypassGate.Violation> violations) {
        StringBuilder out = new StringBuilder();
        for (QueryCompilerBypassGate.Violation violation : violations) {
            out.append(violation.file())
                    .append(": ")
                    .append(violation.rule())
                    .append(" (")
                    .append(violation.detail())
                    .append(")\n");
        }
        return out.toString();
    }
}
