package one.nexus.bootstrap.permission;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import one.nexus.permission.scan.OpsPgDumpHonestyGate;
import org.junit.jupiter.api.Test;

/**
 * G2 leftover: product {@code src/main} does not execute {@code pg_dump};
 * ops scripts stay off SurfaceGuard. Does not change AuditPort. Does not
 * redo #137 / #144 / #150.
 */
class G2OpsPgDumpHonestyTest {
    @Test
    void productMainHasNoOpsPgDumpProcess() throws Exception {
        Path repo = OpsPgDumpHonestyGate.findRepoRoot(Path.of(System.getProperty("user.dir")));
        List<OpsPgDumpHonestyGate.Violation> violations = new OpsPgDumpHonestyGate().scanProduct(repo);
        assertTrue(violations.isEmpty(), () -> {
            StringBuilder out = new StringBuilder("ops pg_dump honesty gate failed:\n");
            for (OpsPgDumpHonestyGate.Violation violation : violations) {
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
