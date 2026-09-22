package one.nexus.permission.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * G2 leftover honesty beyond #124 / #137: report query/export and physical
 * dump / raw export stay on SurfaceGuard; live JAR is honest SKIP.
 * Does not redo #122 / #124 / #137 or H5 form.write leftovers.
 * Does not claim product G2 close.
 */
class G2ReportPhysicalDumpHonestyTest {

    @Test
    void catalogsAndLeftoverDocsStayAlwaysGreen() {
        Path repo = G2ReportPhysicalDumpHonesty.findRepoRoot(Path.of(System.getProperty("user.dir")));
        assertTrue(G2ReportPhysicalDumpHonesty.reportCellsMatchCatalog());
        assertTrue(G2ReportPhysicalDumpHonesty.dumpCellsMatchCatalog());
        assertTrue(G2ReportPhysicalDumpHonesty.leftoverDocsStateBoundary(repo));
        assertTrue(G2ReportPhysicalDumpHonesty.reportControllerUsesSurfaceGuard(repo));
        assertTrue(G2ReportPhysicalDumpHonesty.dumpControllerUsesSurfaceGuard(repo));
        assertEquals("ledger", G2ReportPhysicalDumpHonesty.REPORT_QUERY_RESOURCE);
        assertEquals("read", G2ReportPhysicalDumpHonesty.REPORT_QUERY_ACTION);
        assertEquals("ledger.export.csv", G2ReportPhysicalDumpHonesty.REPORT_EXPORT_RESOURCE);
        assertEquals("submit", G2ReportPhysicalDumpHonesty.REPORT_EXPORT_ACTION);
        assertEquals("ledger", G2ReportPhysicalDumpHonesty.DUMP_READ_RESOURCE);
        assertEquals("submit", G2ReportPhysicalDumpHonesty.DUMP_EXPORT_ACTION);
        assertEquals(G2ReportPhysicalDumpHonesty.REPORT_EXPORT_RESOURCE, SurfaceCatalog.EXPORT_RESOURCE);
        assertEquals(G2ReportPhysicalDumpHonesty.DUMP_EXPORT_RESOURCE, SurfaceCatalog.EXPORT_RESOURCE);
    }

    @Test
    void liveProbeCannotClaimAJarWalk() {
        Path repo = G2ReportPhysicalDumpHonesty.findRepoRoot(Path.of(System.getProperty("user.dir")));
        G2ReportPhysicalDumpHonesty.Inventory inventory = G2ReportPhysicalDumpHonesty.probe(repo);
        assertEquals("SKIP", inventory.liveJarWalk());
        assertEquals("SKIP", inventory.liveReportWalk());
        assertEquals("SKIP", inventory.liveDumpWalk());
        assertFalse(inventory.skipReason().isBlank());
        assertTrue(inventory.leftoverDocsPresent());
        assertTrue(inventory.reportOnSurfaceGuard());
        assertTrue(inventory.dumpOnSurfaceGuard());
        assertTrue(inventory.reportUsesAuthorizedSubmit());
        assertTrue(inventory.dumpUsesWriteProjected());
        assertTrue(inventory.leftoverRowsKept());
        assertFalse(inventory.reportIsPhysicalDump());
        assertFalse(inventory.dumpIsReportTaskSubmit());

        Map<String, String> report = G2ReportPhysicalDumpHonesty.honestSkipTemplate(inventory);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        G2ReportPhysicalDumpHonesty.verify(report);
        assertEquals(G2ReportPhysicalDumpHonesty.G2_CLOSE, report.get("g2_close"));
        assertEquals("PASS", report.get("report_surface_harness"));
        assertEquals("PASS", report.get("dump_surface_harness"));
        assertEquals("PASS", report.get("report_uses_authorized_submit"));
        assertEquals("PASS", report.get("dump_uses_write_projected"));
        assertEquals("false", report.get("report_is_physical_dump"));
        assertEquals("false", report.get("dump_is_report_task_submit"));
        assertEquals("true", report.get("leftover_rows_all"));
        assertEquals("false", report.get("redone_122"));
        assertEquals("false", report.get("redone_124"));
        assertEquals("false", report.get("redone_137"));
        assertEquals("false", report.get("redone_204"));
        assertEquals("false", report.get("p0_m0_redone"));
        assertEquals("closed_unchanged", report.get("g18_status"));
        assertTrue(report.get("leftover_rows_kept").contains("g2-report-surface-guard"));
        assertTrue(report.get("leftover_rows_kept").contains("g2-physical-dump-surface"));
        assertTrue(report.get("leftover_rows_kept").contains("g9-sentinel-cluster-remainder-honesty"));
    }

    @Test
    void honestyGateAcceptsHonestSkipFixture() {
        G2ReportPhysicalDumpHonesty.verify(G2ReportPhysicalDumpHonesty.parseReport(fixture("honest-skip.env")));
    }

    @Test
    void honestyGateRejectsFakeLiveJarPass() {
        Map<String, String> fake = new HashMap<>(
                G2ReportPhysicalDumpHonesty.parseReport(fixture("fake-live-jar-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G2ReportPhysicalDumpHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("live_jar_walk")
                || ex.getMessage().contains("live_report")
                || ex.getMessage().contains("live_dump"));
    }

    @Test
    void honestyGateRejectsG2CloseClaim() {
        Map<String, String> fake = new HashMap<>(
                G2ReportPhysicalDumpHonesty.parseReport(fixture("fake-g2-close.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G2ReportPhysicalDumpHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("g2_close"));
    }

    @Test
    void honestyGateRejectsSiblingRedosAndReportAsDump() {
        IllegalArgumentException redone = assertThrows(IllegalArgumentException.class,
                () -> G2ReportPhysicalDumpHonesty.verify(
                        G2ReportPhysicalDumpHonesty.parseReport(fixture("fake-redone.env"))));
        assertTrue(redone.getMessage().contains("redone_124")
                || redone.getMessage().contains("redone_137")
                || redone.getMessage().contains("redone_122"));

        IllegalArgumentException asDump = assertThrows(IllegalArgumentException.class,
                () -> G2ReportPhysicalDumpHonesty.verify(
                        G2ReportPhysicalDumpHonesty.parseReport(fixture("fake-report-is-dump.env"))));
        assertTrue(asDump.getMessage().contains("report_is_physical_dump")
                || asDump.getMessage().contains("dump_is_report_task_submit")
                || asDump.getMessage().contains("report_uses_authorized_submit")
                || asDump.getMessage().contains("dump_uses_write_projected"));
    }

    @Test
    void docsReadmeKeepsAllSiblingLeftoverRows() throws IOException {
        String text = readExisting(repoFile("docs/README.md"));
        for (String row : G2ReportPhysicalDumpHonesty.SIBLING_README_ROWS) {
            assertTrue(text.contains(row), "missing leftover row " + row);
        }
        assertTrue(text.contains("[g2-report-surface-guard](./g2-report-surface-guard.md)"));
        assertTrue(text.contains("[g2-physical-dump-surface](./g2-physical-dump-surface.md)"));
        assertTrue(text.contains("[g2-report-physical-dump-honesty](./g2-report-physical-dump-honesty.md)"));
        assertTrue(text.contains("[g2-surfaceguard-bypass-ci](./g2-surfaceguard-bypass-ci.md)"));
        assertTrue(text.contains("[g2-h5-every-page-form-write-honesty](./g2-h5-every-page-form-write-honesty.md)"));
        assertTrue(text.contains("[g2-ops-pg-dump-honesty](./g2-ops-pg-dump-honesty.md)"));
        assertTrue(text.contains("[g9-sentinel-cluster-remainder-honesty](./g9-sentinel-cluster-remainder-honesty.md)"));
        assertTrue(text.contains("[g5-publishhook-ddl-honesty](./g5-publishhook-ddl-honesty.md)"));
        assertTrue(text.contains("[g17-gm-tls-nginx-handshake-honesty](./g17-gm-tls-nginx-handshake-honesty.md)"));
        assertTrue(text.contains("[g4-export-wizard-deny-honesty](./g4-export-wizard-deny-honesty.md)"));
        assertTrue(text.contains("[g10-leftover-honesty](./g10-leftover-honesty.md)"));
        assertTrue(text.contains("[g5-mid-publish-failure-honesty](./g5-mid-publish-failure-honesty.md)"));
        assertTrue(text.contains("[g3-outbox-consume-crash-honesty](./g3-outbox-consume-crash-honesty.md)"));
        assertTrue(text.contains("[g17-mfa-recovery-vue-otp-honesty](./g17-mfa-recovery-vue-otp-honesty.md)"));
        assertFalse(text.toLowerCase(Locale.ROOT).contains("product g2 closed"));
        assertFalse(text.contains("产品级 G2 已关闭"));

        String claims = readExisting(repoFile("docs/planning/团队领取任务清单_V1.1.md"));
        assertTrue(claims.contains("g2-report-surface-guard.md"));
        assertTrue(claims.contains("g2-physical-dump-surface.md"));
        assertTrue(claims.contains("g2-report-physical-dump-honesty.md"));
        assertTrue(claims.contains("不宣称产品 G2 关闭"));
        assertTrue(claims.contains("#124") && claims.contains("#137"));
        assertTrue(claims.contains("不重做 #122/#124/#137")
                || claims.contains("leftover honesty beyond #124/#137"));
        assertFalse(claims.contains("产品 G2 已关闭"));
    }

    private static Path fixture(String name) {
        Path first = Path.of("scripts/g2-report-physical-dump-honesty/fixtures/" + name);
        if (first.toFile().isFile()) {
            return first;
        }
        return Path.of("../scripts/g2-report-physical-dump-honesty/fixtures/" + name);
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
