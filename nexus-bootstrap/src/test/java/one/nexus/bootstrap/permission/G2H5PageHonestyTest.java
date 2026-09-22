package one.nexus.bootstrap.permission;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import one.nexus.permission.scan.H5PageSurfaceHonestyGate;
import org.junit.jupiter.api.Test;

/**
 * G2 leftover: product {@code /m/*} routes stay classified covered vs open.
 * Does not change AuditPort. Does not redo #116 / #150 / #144 / #137.
 */
class G2H5PageHonestyTest {
    @Test
    void productRouterInventoryIsHonest() throws Exception {
        Path repo = H5PageSurfaceHonestyGate.findRepoRoot(Path.of(System.getProperty("user.dir")));
        List<H5PageSurfaceHonestyGate.Violation> violations = new H5PageSurfaceHonestyGate().scanProduct(repo);
        assertTrue(violations.isEmpty(), () -> {
            StringBuilder out = new StringBuilder("H5 every-page honesty gate failed:\n");
            for (H5PageSurfaceHonestyGate.Violation violation : violations) {
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
