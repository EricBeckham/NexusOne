package one.nexus.bootstrap.permission;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import one.nexus.permission.scan.SurfaceGuardBypassGate;
import org.junit.jupiter.api.Test;

/**
 * G2 leftover: product {@code src/main} has no raw PermissionEngine /
 * IsolatedExport / unguarded P3 surface outside the documented SurfaceGuard
 * allowlist. Does not invent a second TenantContext and does not change
 * AuditPort. Orthogonal to the G1 QueryCompiler JDBC gate.
 */
class G2SurfaceGuardBypassTest {
    @Test
    void productMainHasNoSurfaceGuardBypass() throws Exception {
        Path repo = SurfaceGuardBypassGate.findRepoRoot(Path.of(System.getProperty("user.dir")));
        List<SurfaceGuardBypassGate.Violation> violations = new SurfaceGuardBypassGate().scanProduct(repo);
        assertTrue(violations.isEmpty(), () -> {
            StringBuilder out = new StringBuilder("SurfaceGuard bypass gate failed:\n");
            for (SurfaceGuardBypassGate.Violation violation : violations) {
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
