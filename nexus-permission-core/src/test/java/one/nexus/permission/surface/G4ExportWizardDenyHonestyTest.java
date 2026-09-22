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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.common.trace.TraceIds;

/**
 * Thin honesty leftover after #166 / #136. In-memory SurfaceGuard deny and
 * export-wizard complete-deny harnesses PASS. Live JAR / browser / {@code :5432}
 * / Keycloak stay SKIP unless observed. Does not redo #136/#152/#166/#190
 * product suites and does not claim product G4 close.
 */
class G4ExportWizardDenyHonestyTest {

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
        AccessContextHolder.clear();
        TraceIds.clearMdc();
    }

    @Test
    void inMemoryGuardDenyHarnessWritesPair() {
        G4ExportWizardDenyHonesty.GuardDenyProof proof = G4ExportWizardDenyHonesty.proveGuardDeny();
        assertTrue(proof.permissionDenied());
        assertTrue(proof.guardReject());
        assertTrue(proof.chainIntact());
        assertEquals("P2.PERM.0003", proof.errorCode());
        assertEquals("PASS", proof.verdict());
    }

    @Test
    void inMemoryExportWizardDenyHarnessWritesPairAndSkipsIsolatedExport() {
        G4ExportWizardDenyHonesty.WizardDenyProof proof = G4ExportWizardDenyHonesty.proveExportWizardDeny();
        assertTrue(proof.begunOnBeginTrace());
        assertTrue(proof.permissionDenied());
        assertTrue(proof.guardReject());
        assertTrue(proof.noCompleted());
        assertTrue(proof.noSubmitted());
        assertTrue(proof.chainIntact());
        assertEquals("P2.PERM.0003", proof.errorCode());
        assertEquals("PASS", proof.verdict());
    }

    @Test
    void sameTxAppendContractIsUnchanged() throws IOException {
        String auditPort = readExisting(G4ExportWizardDenyHonesty.auditPortSource());
        String surfaceGuard = readExisting(G4ExportWizardDenyHonesty.surfaceGuardSource());
        String authorizedExport = readExisting(G4ExportWizardDenyHonesty.authorizedExportSource());
        assertTrue(G4ExportWizardDenyHonesty.sameTxAppendUnchanged(auditPort, surfaceGuard, authorizedExport));
    }

    @Test
    void liveProbeCannotClaimLiveWalks() {
        G4ExportWizardDenyHonesty.LiveInventory live = G4ExportWizardDenyHonesty.probeLive();
        assertEquals("SKIP", live.liveJarWizardDeny());
        assertEquals("SKIP", live.liveBrowserWizard());
        assertEquals("SKIP", live.live5432Jdbc());
        assertEquals("SKIP", live.liveKeycloakIdp());
        assertFalse(live.liveJarSkipReason().isBlank());
        assertFalse(live.liveBrowserSkipReason().isBlank());
        assertFalse(live.liveJdbcSkipReason().isBlank());
        assertFalse(live.liveKeycloakSkipReason().isBlank());

        Map<String, String> report = G4ExportWizardDenyHonesty.honestSkipTemplate(
                G4ExportWizardDenyHonesty.proveGuardDeny(),
                G4ExportWizardDenyHonesty.proveExportWizardDeny(),
                live);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        G4ExportWizardDenyHonesty.verify(report);
        assertEquals("PASS", report.get("guard_deny_harness"));
        assertEquals("PASS", report.get("export_wizard_deny_harness"));
        assertEquals("PASS", report.get("same_tx_append_unchanged"));
        assertEquals("SKIP", report.get("live_jar_wizard_deny"));
        assertEquals("SKIP", report.get("live_browser_wizard"));
        assertEquals("SKIP", report.get("live_5432_jdbc"));
        assertEquals("SKIP", report.get("live_keycloak_idp"));
        assertEquals(G4ExportWizardDenyHonesty.G4_CLOSE, report.get("g4_close"));
        assertEquals("false", report.get("redo_136"));
        assertEquals("false", report.get("redo_152"));
        assertEquals("false", report.get("redo_166"));
        assertEquals("false", report.get("redo_190"));
        assertEquals("false", report.get("audit_port_changed"));
        assertEquals("closed_unchanged", report.get("g18_status"));
        assertEquals("false", report.get("p0_m0_redone"));
    }

    @Test
    void honestyGateAcceptsHonestSkipFixture() {
        G4ExportWizardDenyHonesty.verify(G4ExportWizardDenyHonesty.parseReport(fixture("honest-skip.env")));
    }

    @Test
    void honestyGateRejectsFakeLivePass() {
        Map<String, String> fake = new HashMap<>(
                G4ExportWizardDenyHonesty.parseReport(fixture("fake-live-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G4ExportWizardDenyHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("live_jar_wizard_deny"));
    }

    @Test
    void honestyGateRejectsG4CloseClaim() {
        Map<String, String> fake = new HashMap<>(
                G4ExportWizardDenyHonesty.parseReport(fixture("fake-g4-close.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G4ExportWizardDenyHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("g4_close"));
    }

    @Test
    void honestyGateRejectsRedoOfClosedSlices() {
        Map<String, String> fake = new HashMap<>(
                G4ExportWizardDenyHonesty.parseReport(fixture("fake-redo.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G4ExportWizardDenyHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("redo_166")
                || ex.getMessage().contains("redo_136")
                || ex.getMessage().contains("redo_152")
                || ex.getMessage().contains("redo_190"));
    }

    @Test
    void leftoverDocsKeepAllSiblingRows() throws IOException {
        String readme = readExisting(G4ExportWizardDenyHonesty.readmeSource());
        assertTrue(G4ExportWizardDenyHonesty.leftoverRowsKept(readme));
        for (String row : G4ExportWizardDenyHonesty.SIBLING_ROWS) {
            assertTrue(readme.contains(row), row);
        }
        assertFalse(readme.toLowerCase(Locale.ROOT).contains("product g4 closed"));
        assertFalse(readme.contains("产品 G4 已关闭"));

        String leftover = readExisting(repoFile("docs/g4-export-wizard-deny-honesty.md"));
        assertTrue(leftover.contains("#166"));
        assertTrue(leftover.contains("#136"));
        assertTrue(leftover.contains("does **not** claim") || leftover.contains("Does **not** claim"));
        assertTrue(leftover.contains("does **not** redo") || leftover.contains("Do not redo"));
        assertFalse(leftover.toLowerCase(Locale.ROOT).contains("product g4 is closed"));
        assertFalse(leftover.contains("产品 G4 已关闭"));
    }

    @Test
    void claimListG4RowStaysHonest() throws IOException {
        String text = readExisting(repoFile("docs/planning/团队领取任务清单_V1.1.md"));
        assertTrue(text.contains("g4-export-wizard-deny.md"));
        assertTrue(text.contains("g4-export-wizard-deny-honesty.md"));
        assertTrue(text.contains("g4-guard-deny-audit.md"));
        assertTrue(text.contains("不要重做 #136"));
        assertTrue(text.contains("不要重做 #166"));
        assertTrue(text.contains("不要宣称产品 G4 关闭"));
        assertTrue(text.contains("不要重开 G18"));
        assertFalse(text.contains("产品 G4 已关闭"));
    }

    private static Path fixture(String name) {
        Path first = Path.of("scripts/g4-export-wizard-deny-honesty/fixtures/" + name);
        if (first.toFile().isFile()) {
            return first;
        }
        return Path.of("../scripts/g4-export-wizard-deny-honesty/fixtures/" + name);
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
