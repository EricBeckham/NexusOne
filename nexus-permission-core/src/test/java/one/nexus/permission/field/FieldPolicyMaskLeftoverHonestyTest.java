package one.nexus.permission.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * G1 leftover beyond #162: live FieldPolicy mask / second-auth PASS/SKIP.
 * Does not redo #115 / #130 / #134 / #142 / #146 / #162 / #168.
 * Does not claim product G1 close. G18 stays closed.
 */
class FieldPolicyMaskLeftoverHonestyTest {

    @Test
    void liveInventoryIsSkipWithoutObservedWalk() {
        FieldPolicyMaskLeftoverHonesty.Inventory inventory = FieldPolicyMaskLeftoverHonesty.probeLive();
        assertEquals("SKIP", inventory.liveApiMask());
        assertEquals("SKIP", inventory.liveExportMask());
        assertEquals("SKIP", inventory.liveSecondAuth());
        assertEquals("SKIP", inventory.uiH5Report());
        assertFalse(inventory.skipReason().isBlank());
        if (!inventory.livePresent()) {
            assertFalse(inventory.endpointSet() && inventory.issuedContextSet() && inventory.listenerPresent());
        }

        Map<String, String> report = FieldPolicyMaskLeftoverHonesty.honestSkipTemplate(inventory);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        FieldPolicyMaskLeftoverHonesty.verify(report);
        assertEquals("NOT_CLAIMED", report.get("g1_close"));
        assertEquals("PASS", report.get("leftover_beside_ast"));
        assertEquals("false", report.get("mask_on_ast"));
        assertEquals("false", report.get("second_auth_on_ast"));
        assertEquals("false", report.get("isolated_export_applies_mask"));
        assertEquals("FieldPolicyProjector", report.get("leftover_surface"));
        assertEquals("true", report.get("leftover_rows_kept"));
        assertTrue(report.get("leftover_rows").contains("g1-datascope-or-in-ast"));
        assertTrue(report.get("leftover_rows").contains("g1-fieldpolicy-mask-second-auth"));
        assertEquals("false", report.get("redone_162"));
        assertEquals("false", report.get("redone_168"));
        assertEquals("false", report.get("p0_m0_redone"));
        assertEquals("closed_unchanged", report.get("g18_status"));
        assertEquals("NOT_CLAIMED", FieldPolicyMaskHonesty.G1_CLOSE);
    }

    @Test
    void honestyGateAcceptsHonestSkipFixture() {
        FieldPolicyMaskLeftoverHonesty.verify(
                FieldPolicyMaskLeftoverHonesty.parseReport(fixture("honest-skip.env")));
    }

    @Test
    void honestyGateRejectsFakeLiveApiMaskPass() {
        Map<String, String> fake = new HashMap<>(
                FieldPolicyMaskLeftoverHonesty.parseReport(fixture("fake-live-api-mask-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> FieldPolicyMaskLeftoverHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("live_api_mask"));
    }

    @Test
    void honestyGateRejectsFakeLiveExportMaskPass() {
        Map<String, String> fake = new HashMap<>(
                FieldPolicyMaskLeftoverHonesty.parseReport(fixture("fake-live-export-mask-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> FieldPolicyMaskLeftoverHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("live_export_mask"));
    }

    @Test
    void honestyGateRejectsFakeLiveSecondAuthPass() {
        Map<String, String> fake = new HashMap<>(
                FieldPolicyMaskLeftoverHonesty.parseReport(fixture("fake-live-second-auth-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> FieldPolicyMaskLeftoverHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("live_second_auth"));
    }

    @Test
    void honestyGateRejectsG1CloseAndMaskOnAst() {
        IllegalArgumentException close = assertThrows(IllegalArgumentException.class,
                () -> FieldPolicyMaskLeftoverHonesty.verify(
                        FieldPolicyMaskLeftoverHonesty.parseReport(fixture("fake-g1-close.env"))));
        assertTrue(close.getMessage().contains("g1_close"));

        IllegalArgumentException mask = assertThrows(IllegalArgumentException.class,
                () -> FieldPolicyMaskLeftoverHonesty.verify(
                        FieldPolicyMaskLeftoverHonesty.parseReport(fixture("fake-mask-on-ast.env"))));
        assertTrue(mask.getMessage().contains("mask_on_ast"));
    }

    @Test
    void honestyGateRejectsSiblingRedosAndDroppedLeftoverRows() {
        Map<String, String> fake = new HashMap<>(
                FieldPolicyMaskLeftoverHonesty.parseReport(fixture("honest-skip.env")));
        fake.put("redone_162", "true");
        IllegalArgumentException redo = assertThrows(IllegalArgumentException.class,
                () -> FieldPolicyMaskLeftoverHonesty.verify(fake));
        assertTrue(redo.getMessage().contains("redone_162"));

        fake.put("redone_162", "false");
        fake.put("leftover_rows", "g1-datascope-or-in-ast");
        IllegalArgumentException rows = assertThrows(IllegalArgumentException.class,
                () -> FieldPolicyMaskLeftoverHonesty.verify(fake));
        assertTrue(rows.getMessage().contains("leftover_rows"));
    }

    @Test
    void docsReadmeKeepsBothSiblingLeftoverRows() throws IOException {
        String text = readExisting(repoFile("docs/README.md"));
        assertTrue(text.contains("[g1-datascope-or-in-ast](./g1-datascope-or-in-ast.md)"));
        assertTrue(text.contains("[g1-fieldpolicy-mask-second-auth](./g1-fieldpolicy-mask-second-auth.md)"));
        assertTrue(text.contains("[g1-fieldpolicy-mask-second-auth-honesty](./g1-fieldpolicy-mask-second-auth-honesty.md)"));
        assertTrue(text.contains("[g4-pkce-spa-verifier-honesty](./g4-pkce-spa-verifier-honesty.md)"));
        assertTrue(text.contains("[g10-identity-drift-honesty](./g10-identity-drift-honesty.md)"));
        assertTrue(text.contains("[g15-native-bridge-honesty](./g15-native-bridge-honesty.md)"));
        assertTrue(text.contains("[s0-secret-getenv-snapshot-honesty](./s0-secret-getenv-snapshot-honesty.md)"));
        assertTrue(text.contains("[g5-browser-failed-publish](./g5-browser-failed-publish.md)"));
        assertFalse(text.toLowerCase().contains("product g1 closed"));
        assertFalse(text.contains("产品 G1 已关闭"));
    }

    private static Path fixture(String name) {
        Path module = Path.of("scripts/g1-fieldpolicy-mask-second-auth-honesty/fixtures", name);
        if (module.toFile().isFile()) {
            return module;
        }
        return Path.of("../scripts/g1-fieldpolicy-mask-second-auth-honesty/fixtures", name);
    }

    private static Path repoFile(String relative) {
        Path here = Path.of(relative);
        if (here.toFile().isFile()) {
            return here;
        }
        return Path.of("..", relative);
    }

    private static String readExisting(Path path) throws IOException {
        return Files.readString(path);
    }
}
