package one.nexus.isolation.scan;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlBypassScannerTest {
    private final SqlBypassScanner scanner = new SqlBypassScanner();
    private final Path fixtures = Path.of("src/test/resources/sql-bypass-fixtures");

    @Test
    void positiveBusinessIsAllowedWhenInfrastructureIsExempt() throws Exception {
        Path positive = fixtures.resolve("positive");
        List<SqlBypassScanner.Violation> violations = scanner.scan(positive, positive.resolve("infrastructure"));
        assertTrue(violations.isEmpty(), violations::toString);
    }

    @Test
    void jdbcSpringJpaMybatisFixturesAreRejected() throws Exception {
        assertRejected("jdbc", "direct JDBC");
        assertRejected("spring", "Spring JDBC");
        assertRejected("jpa", "JPA native SQL");
        assertRejected("mybatis", "MyBatis literal substitution");
    }

    private void assertRejected(String fixture, String rule) throws Exception {
        Path root = fixtures.resolve("negative").resolve(fixture);
        List<SqlBypassScanner.Violation> violations = scanner.scan(root, null);
        assertFalse(violations.isEmpty(), "expected rejection for " + fixture);
        assertTrue(violations.stream().anyMatch(v -> v.rule().equals(rule)), violations::toString);
        assertEquals(1, violations.stream().map(SqlBypassScanner.Violation::rule).distinct().count());
    }
}
