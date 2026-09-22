package one.nexus.bootstrap.isolation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import one.nexus.isolation.scan.QueryCompilerBypassGate;
import org.junit.jupiter.api.Test;

/**
 * G1 leftover: product {@code src/main} has no raw JDBC / tenant-table SQL
 * outside the documented QueryCompiler allowlist. Does not invent a second
 * TenantContext and does not change AuditPort same-TX joining.
 */
class G1QueryCompilerBypassTest {
    @Test
    void productMainHasNoQueryCompilerBypass() throws Exception {
        Path repo = QueryCompilerBypassGate.findRepoRoot(Path.of(System.getProperty("user.dir")));
        List<QueryCompilerBypassGate.Violation> violations = new QueryCompilerBypassGate().scanProduct(repo);
        assertTrue(violations.isEmpty(), () -> {
            StringBuilder out = new StringBuilder("QueryCompiler bypass gate failed:\n");
            for (QueryCompilerBypassGate.Violation violation : violations) {
                out.append(violation.file())
                        .append(": ")
                        .append(violation.rule())
                        .append(" (")
                        .append(violation.detail())
                        .append(")\n");
            }
            return out.toString();
        });
    }
}
