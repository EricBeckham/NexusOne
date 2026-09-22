package one.nexus.audit.recovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Honesty contract for the product {@link RecoveryPort}. Dry-run stays dry-run.
 * Not minute-level RPO, not product HA, not a product RecoveryPort close.
 */
class DryRunRecoveryPortContractTest {
    @Test
    void productPortNeverClaimsExecutionOrProfile() {
        RecoveryPort port = new DryRunRecoveryPort();
        assertFalse(port.postgresWholeDatabasePlan().profileAccepted());
        assertFalse(port.objectStorePlan().profileAccepted());
        RecoveryDryRunResult pg = port.dryRunPostgres();
        assertFalse(pg.executed());
        assertFalse(pg.profileAccepted());
        assertTrue("DRY_RUN".equals(pg.status()) || "TOOL_MISSING".equals(pg.status()));
        assertTrue(pg.notes().stream().anyMatch(n -> n.contains("profileAccepted remains false")));
        RecoveryDryRunResult objects = port.dryRunObjectStore();
        assertEquals("DRY_RUN", objects.status());
        assertFalse(objects.executed());
        assertFalse(objects.profileAccepted());
        assertTrue(objects.notes().stream().anyMatch(n -> n.contains("G6 / Profile acceptance is not claimed")));
    }

    @Test
    void resultRejectsExecutedPlusProfileAccepted() {
        assertThrows(IllegalArgumentException.class, () -> new RecoveryDryRunResult(
                RecoveryKind.POSTGRES_WHOLE_DATABASE,
                "DONE",
                "pg_dump",
                List.of("must not claim both"),
                true,
                true));
    }

    @Test
    void sourceStaysProbeOnlyAndNeverMentionsHaopan() throws IOException {
        Path file = Path.of("src/main/java/one/nexus/audit/recovery/DryRunRecoveryPort.java");
        assertTrue(Files.isRegularFile(file), file.toAbsolutePath().toString());
        String text = Files.readString(file);
        String lower = text.toLowerCase(Locale.ROOT);
        assertTrue(text.contains("new ProcessBuilder(command, \"--version\")"));
        assertFalse(lower.contains("haopan_platform"));
        assertFalse(lower.contains("haopan"));
        assertFalse(text.contains("DriverManager"));
        assertFalse(text.contains("DataSource"));
        assertFalse(text.contains("jdbc:"));
        assertFalse(lower.contains("pg_restore"));
        assertFalse(text.contains("INSERT "));
        assertFalse(text.contains("UPDATE "));
        assertFalse(text.contains("DELETE "));
        assertFalse(text.contains("COPY "));
        assertFalse(text.contains("TRUNCATE "));
        assertEquals(1, countOccurrences(text, "new ProcessBuilder"), "only the --version probe may spawn a process");
    }

    @Test
    void leftoverDocsStayHonest() throws IOException {
        Path leftover = Path.of("../docs/g6-recovery-port-dry-run.md");
        if (!Files.isRegularFile(leftover)) {
            leftover = Path.of("docs/g6-recovery-port-dry-run.md");
        }
        assertTrue(Files.isRegularFile(leftover), leftover.toAbsolutePath().toString());
        String text = Files.readString(leftover);
        assertTrue(text.contains("dry-run"));
        assertTrue(text.contains("分钟级 RPO") || text.toLowerCase(Locale.ROOT).contains("minute-level rpo"));
        assertFalse(text.contains("产品 RecoveryPort 已关闭"));
        assertFalse(text.toLowerCase(Locale.ROOT).contains("product recoveryport is closed"));
        assertFalse(text.toLowerCase(Locale.ROOT).contains("product recoveryport closed"));
        assertTrue(text.contains("haopan_platform"));
        assertTrue(text.contains("does **not** claim a product RecoveryPort close")
                || text.contains("not a product RecoveryPort close"));
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(needle, from);
            if (at < 0) {
                return count;
            }
            count++;
            from = at + needle.length();
        }
    }
}
