package one.nexus.permission.scan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import one.nexus.permission.surface.OpsPhysicalDumpCatalog;
import org.junit.jupiter.api.Test;

/**
 * G2 leftover gate: an intentional product {@code pg_dump} process fails;
 * product {@code src/main} plus documented ops scripts stay honest.
 */
class OpsPgDumpHonestyGateTest {
    private final OpsPgDumpHonestyGate gate = new OpsPgDumpHonestyGate();
    private final Path fixtures = Path.of("src/test/resources/ops-pg-dump-fixtures");

    @Test
    void intentionalProductPgDumpFailsTheGate() throws Exception {
        List<OpsPgDumpHonestyGate.Violation> violations = gate.scan(fixtures.resolve("negative"), false);
        assertFalse(violations.isEmpty(), "expected product ProcessBuilder(pg_dump) to fail");
        assertTrue(
                violations.stream().anyMatch(v -> OpsPgDumpHonestyGate.OPS_PG_DUMP_IN_PRODUCT.equals(v.rule())
                        && v.file().getFileName().toString().equals("RawPgDumpExport.java")),
                violations::toString);
    }

    @Test
    void commentOnlyHonestyNotePasses() throws Exception {
        List<OpsPgDumpHonestyGate.Violation> violations = gate.scan(fixtures.resolve("positive"), false);
        assertTrue(violations.isEmpty(), violations::toString);
    }

    @Test
    void catalogDocumentsBoundaryAndOpsPaths() {
        assertTrue(OpsPhysicalDumpCatalog.BOUNDARY.contains("out of product SurfaceGuard"));
        assertTrue(OpsPhysicalDumpCatalog.PRODUCT_PATH.contains("AuthorizedExport.writeProjected"));
        assertEqualsProbe();
        assertFalse(OpsPhysicalDumpCatalog.OPS_SCRIPT_PATHS.isEmpty());
        assertTrue(OpsPhysicalDumpCatalog.PRODUCT_DUMP_SURFACES.stream()
                .anyMatch(p -> p.endsWith("DumpController.java")));
        assertTrue(OpsPhysicalDumpCatalog.PRODUCT_DUMP_SURFACES.stream()
                .anyMatch(p -> p.endsWith("AuthorizedExport.java")));
    }

    @Test
    void productMainAndOpsScriptsPass() throws Exception {
        Path repo = OpsPgDumpHonestyGate.findRepoRoot(Path.of(System.getProperty("user.dir")));
        List<OpsPgDumpHonestyGate.Violation> violations = gate.scanProduct(repo);
        assertTrue(violations.isEmpty(), () -> "product + ops honesty failed:\n" + format(violations));
    }

    @Test
    void productDumpSurfaceUsesAuthorizedExportNotPgDump() throws Exception {
        Path repo = OpsPgDumpHonestyGate.findRepoRoot(Path.of(System.getProperty("user.dir")));
        Path dump = repo.resolve("nexus-p3-studio/src/main/java/one/nexus/p3/dump/DumpController.java");
        String text = Files.readString(dump, StandardCharsets.UTF_8);
        assertTrue(text.contains("AuthorizedExport.writeProjected"), dump.toString());
        assertFalse(text.contains("ProcessBuilder"), dump.toString());
        assertFalse(text.contains("Runtime.getRuntime"), dump.toString());
        Path export = repo.resolve(
                "nexus-permission-core/src/main/java/one/nexus/permission/surface/AuthorizedExport.java");
        String exportText = Files.readString(export, StandardCharsets.UTF_8);
        assertFalse(exportText.contains("ProcessBuilder"), export.toString());
        assertFalse(exportText.contains("\"pg_dump\""), export.toString());
    }

    @Test
    void dryRunRecoveryPortStaysVersionProbe() throws Exception {
        Path repo = OpsPgDumpHonestyGate.findRepoRoot(Path.of(System.getProperty("user.dir")));
        Path probe = repo.resolve(OpsPhysicalDumpCatalog.ALLOWED_PRODUCT_PROBE_PATHS.getFirst());
        String text = Files.readString(probe, StandardCharsets.UTF_8);
        assertTrue(text.contains("new ProcessBuilder(command, \"--version\")"), probe.toString());
        assertTrue(text.contains("probe(\"pg_dump\")"), probe.toString());
        assertFalse(text.contains("\"--format=custom\""), probe.toString());
        assertFalse(text.contains("pg_restore"), probe.toString());
    }

    private static void assertEqualsProbe() {
        assertTrue(OpsPhysicalDumpCatalog.ALLOWED_PRODUCT_PROBE.contains("--version"));
        assertTrue(OpsPhysicalDumpCatalog.ALLOWED_PRODUCT_PROBE_PATHS.stream()
                .anyMatch(p -> p.endsWith("DryRunRecoveryPort.java")));
    }

    private static String format(List<OpsPgDumpHonestyGate.Violation> violations) {
        StringBuilder out = new StringBuilder();
        for (OpsPgDumpHonestyGate.Violation violation : violations) {
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
