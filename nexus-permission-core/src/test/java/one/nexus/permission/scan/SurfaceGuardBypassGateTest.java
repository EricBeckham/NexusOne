package one.nexus.permission.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * G2 leftover gate: an intentional SurfaceGuard / PermissionEngine bypass
 * fails; product {@code src/main} with the documented allowlist passes.
 */
class SurfaceGuardBypassGateTest {
    private final SurfaceGuardBypassGate gate = new SurfaceGuardBypassGate();
    private final Path fixtures = Path.of("src/test/resources/surfaceguard-bypass-fixtures");

    @Test
    void intentionalEngineEvaluateFailsTheGate() throws Exception {
        List<SurfaceGuardBypassGate.Violation> violations =
                gate.scan(fixtures.resolve("negative/engine"), false);
        assertFalse(violations.isEmpty(), "expected PermissionEngine.evaluate caller to fail");
        assertTrue(
                violations.stream().anyMatch(v -> SurfaceGuardBypassGate.RAW_ENGINE.equals(v.rule())),
                violations::toString);
    }

    @Test
    void intentionalIsolatedExportFailsTheGate() throws Exception {
        List<SurfaceGuardBypassGate.Violation> violations =
                gate.scan(fixtures.resolve("negative/export"), false);
        assertFalse(violations.isEmpty(), "expected IsolatedExport.submit caller to fail");
        assertTrue(
                violations.stream().anyMatch(v -> SurfaceGuardBypassGate.ISOLATED_EXPORT.equals(v.rule())),
                violations::toString);
    }

    @Test
    void intentionalDecisionTrueSkipFailsTheGate() throws Exception {
        List<SurfaceGuardBypassGate.Violation> violations =
                gate.scan(fixtures.resolve("negative/skip"), false);
        assertFalse(violations.isEmpty(), "expected new Decision(true) skip to fail");
        assertTrue(
                violations.stream().anyMatch(v -> SurfaceGuardBypassGate.RAW_PERMISSION_SKIP.equals(v.rule())),
                violations::toString);
    }

    @Test
    void intentionalUnguardedP3SurfaceFailsTheGate() throws Exception {
        List<SurfaceGuardBypassGate.Violation> violations =
                gate.scan(fixtures.resolve("negative/layer"), false);
        assertEquals(1, violations.size(), violations::toString);
        assertEquals(SurfaceGuardBypassGate.UNGUARDED_P3, violations.getFirst().rule());
        assertEquals("DumpController.java", violations.getFirst().file().getFileName().toString());
    }

    @Test
    void cleanSurfaceGuardFixturePasses() throws Exception {
        List<SurfaceGuardBypassGate.Violation> violations =
                gate.scan(fixtures.resolve("positive/clean"), false);
        assertTrue(violations.isEmpty(), violations::toString);
    }

    @Test
    void intentionalUnguardedDumpAndRawProjectedDumpFailTheGate() throws Exception {
        List<SurfaceGuardBypassGate.Violation> violations =
                gate.scan(fixtures.resolve("negative/dump"), false);
        assertTrue(
                violations.stream().anyMatch(v -> SurfaceGuardBypassGate.UNGUARDED_P3.equals(v.rule())
                        && v.file().getFileName().toString().equals("RawDumpController.java")),
                violations::toString);
        assertTrue(
                violations.stream().anyMatch(v -> SurfaceGuardBypassGate.ISOLATED_EXPORT.equals(v.rule())
                        && v.file().getFileName().toString().equals("RawProjectedDump.java")),
                violations::toString);
    }

    @Test
    void allowlistAcceptsCatalogAndRejectsMarketDump() throws Exception {
        List<SurfaceGuardBypassGate.Violation> violations =
                gate.scan(fixtures.resolve("mini-product"), true);
        assertFalse(violations.isEmpty(), "mini-product RawExportDump / RawEngineSkip must fail");
        assertTrue(
                violations.stream()
                        .allMatch(v -> {
                            String name = v.file().getFileName().toString();
                            return name.equals("RawExportDump.java") || name.equals("RawEngineSkip.java");
                        }),
                violations::toString);
        assertTrue(
                violations.stream().anyMatch(v -> SurfaceGuardBypassGate.ISOLATED_EXPORT.equals(v.rule())),
                violations::toString);
        assertTrue(
                violations.stream().anyMatch(v -> SurfaceGuardBypassGate.RAW_ENGINE.equals(v.rule())),
                violations::toString);
        assertTrue(
                violations.stream().noneMatch(v -> v.file().getFileName().toString().equals("CatalogOnly.java")),
                violations::toString);
        assertTrue(
                violations.stream().noneMatch(v -> v.file().getFileName().toString().equals("GuardedForm.java")),
                violations::toString);
    }

    @Test
    void productMainPassesWithDocumentedAllowlist() throws Exception {
        Path repo = SurfaceGuardBypassGate.findRepoRoot(Path.of(System.getProperty("user.dir")));
        List<SurfaceGuardBypassGate.Violation> violations = gate.scanProduct(repo);
        assertTrue(violations.isEmpty(), () -> "product src/main must pass:\n" + format(violations));
    }

    @Test
    void allowlistDocumentsEngineExportAndLeftovers() {
        assertFalse(SurfaceGuardBypassAllowlist.ENGINE_PATHS.isEmpty());
        assertTrue(SurfaceGuardBypassAllowlist.ENGINE_PATHS.stream()
                .anyMatch(p -> p.endsWith("PermissionEngine.java")));
        assertTrue(SurfaceGuardBypassAllowlist.ENGINE_PATHS.stream()
                .anyMatch(p -> p.endsWith("PermissionService.java")));
        assertTrue(SurfaceGuardBypassAllowlist.EXPORT_PATHS.stream()
                .anyMatch(p -> p.endsWith("AuthorizedExport.java")));
        assertTrue(SurfaceGuardBypassAllowlist.EXPORT_PATHS.stream()
                .anyMatch(p -> p.endsWith("IsolatedExport.java")));
        assertTrue(SurfaceGuardBypassAllowlist.UNGUARDED_P3_LEFTOVER_PATHS.isEmpty());
        assertTrue(SurfaceGuardBypassAllowlist.GUARD_TOKENS.contains("SurfaceGuard"));
        assertTrue(SurfaceGuardBypassAllowlist.GUARD_TOKENS.contains("TodoSurfaceGuard"));
        assertTrue(SurfaceGuardBypassAllowlist.GUARD_TOKENS.contains("ProcessDefinitionSurfaceGuard"));
        assertFalse(
                SurfaceGuardBypassAllowlist.PRODUCT_SOURCE_ROOTS.contains("poc"),
                "poc is not a product source root");
    }

    private static String format(List<SurfaceGuardBypassGate.Violation> violations) {
        StringBuilder out = new StringBuilder();
        for (SurfaceGuardBypassGate.Violation violation : violations) {
            out.append(violation.file())
                    .append(": ")
                    .append(violation.rule())
                    .append(" (")
                    .append(violation.detail())
                    .append(")\n");
        }
        return out.toString();
    }
}
