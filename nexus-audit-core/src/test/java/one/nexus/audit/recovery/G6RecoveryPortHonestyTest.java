package one.nexus.audit.recovery;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Thin honesty leftover after #135. Does not redo the dry-run mutation /
 * Zonky ITs. Documents that #135 dry-run is not product HA, not
 * minute-level RPO, and not delivery-grade HA. Does not claim a product
 * RecoveryPort close.
 */
class G6RecoveryPortHonestyTest {

    @Test
    void leftoverDocsState135IsNotProductHaOrDeliveryHa() throws IOException {
        String text = readExisting(honestyDoc());
        String lower = text.toLowerCase(Locale.ROOT);
        assertTrue(text.contains("#135"));
        assertTrue(text.contains("dry-run"));
        assertTrue(lower.contains("minute-level rpo") || text.contains("分钟级 RPO"));
        assertTrue(lower.contains("product ha") || text.contains("产品 HA"));
        assertTrue(lower.contains("delivery-grade ha") || text.contains("交付级 HA"));
        assertTrue(text.contains("does **not** claim a product RecoveryPort close")
                || text.contains("not a product RecoveryPort close"));
        assertTrue(text.contains("does **not** redo #135") || text.contains("Do not redo #135"));
        assertTrue(text.contains("Keep both leftover rows"));
        assertFalse(text.contains("产品 RecoveryPort 已关闭"));
        assertFalse(lower.contains("product recoveryport is closed"));
        assertFalse(lower.contains("product recoveryport closed"));
        assertFalse(lower.contains("product ha closed"));
        assertFalse(lower.contains("delivery ha closed"));
    }

    @Test
    void docsReadmeKeepsBothLeftoverRows() throws IOException {
        String text = readExisting(repoFile("docs/README.md"));
        assertTrue(text.contains("[g6-recovery-port-dry-run](./g6-recovery-port-dry-run.md)"));
        assertTrue(text.contains("[g6-recovery-port-honesty](./g6-recovery-port-honesty.md)"));
        assertTrue(text.contains("g10-identity-drift-dry-run"));
        assertTrue(text.contains("s0-secret-rotate-without-restart"));
        assertTrue(text.contains("g1-fieldpolicy-mask-second-auth"));
        assertTrue(text.contains("g17-vue-otp-writeback"));
        assertTrue(text.contains("g17-gm-tls-nginx-handshake"));
        assertTrue(text.contains("g9-live-multi-jvm-replica"));
        assertFalse(text.toLowerCase(Locale.ROOT).contains("product recoveryport closed"));
    }

    @Test
    void claimListG6RowStaysHonest() throws IOException {
        String text = readExisting(repoFile("docs/planning/团队领取任务清单_V1.1.md"));
        assertTrue(text.contains("g6-recovery-port-dry-run.md"));
        assertTrue(text.contains("g6-recovery-port-honesty.md"));
        assertTrue(text.contains("#135"));
        assertTrue(text.contains("不要重做 #135"));
        assertTrue(text.contains("分钟级 RPO"));
        assertTrue(text.contains("交付级 HA"));
        assertTrue(text.contains("不要宣称产品 `RecoveryPort`"));
        assertFalse(text.contains("产品 RecoveryPort 已关闭"));
        assertFalse(text.contains("产品 G6 已关闭"));
    }

    @Test
    void thinDryRunApiStillRefusesExecutionAndProfile() {
        RecoveryPort port = new DryRunRecoveryPort();
        RecoveryDryRunResult pg = port.dryRunPostgres();
        assertFalse(pg.executed());
        assertFalse(pg.profileAccepted());
        assertTrue("DRY_RUN".equals(pg.status()) || "TOOL_MISSING".equals(pg.status()));
        RecoveryDryRunResult objects = port.dryRunObjectStore();
        assertFalse(objects.executed());
        assertFalse(objects.profileAccepted());
        assertFalse(port.postgresWholeDatabasePlan().profileAccepted());
        assertFalse(port.objectStorePlan().profileAccepted());
    }

    private static Path honestyDoc() {
        return repoFile("docs/g6-recovery-port-honesty.md");
    }

    private static Path repoFile(String relative) {
        Path nested = Path.of("..", relative);
        if (Files.isRegularFile(nested)) {
            return nested;
        }
        return Path.of(relative);
    }

    private static String readExisting(Path file) throws IOException {
        assertTrue(Files.isRegularFile(file), file.toAbsolutePath().toString());
        return Files.readString(file);
    }
}
