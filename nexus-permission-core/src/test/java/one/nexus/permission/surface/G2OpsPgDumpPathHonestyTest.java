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
 * G2 leftover honesty beyond #155: ops {@code pg_dump} stays outside
 * product SurfaceGuard; live product-database dump is honest SKIP.
 * Does not redo #137 / #144 / #150 / #155. Does not claim product G2 close.
 */
class G2OpsPgDumpPathHonestyTest {

    @Test
    void catalogAndLeftoverDocsStayAlwaysGreen() {
        Path repo = G2OpsPgDumpPathHonesty.findRepoRoot(Path.of(System.getProperty("user.dir")));
        assertTrue(G2OpsPgDumpPathHonesty.opsDumpOutsideSurfaceGuard());
        assertTrue(G2OpsPgDumpPathHonesty.leftoverDocsStateBoundary(repo));
        assertTrue(G2OpsPgDumpPathHonesty.productDumpUsesAuthorizedExport(repo));
        assertTrue(G2OpsPgDumpPathHonesty.opsScriptsAreRunbook(repo));
        assertEquals("AuthorizedExport.writeProjected", G2OpsPgDumpPathHonesty.PRODUCT_PATH);
        assertTrue(OpsPhysicalDumpCatalog.BOUNDARY.contains("out of product SurfaceGuard"));
    }

    @Test
    void liveProbeCannotClaimAProductDatabaseDump() {
        Path repo = G2OpsPgDumpPathHonesty.findRepoRoot(Path.of(System.getProperty("user.dir")));
        G2OpsPgDumpPathHonesty.Inventory inventory = G2OpsPgDumpPathHonesty.probe(repo);
        assertEquals("SKIP", inventory.liveProductDbPgDump());
        assertTrue(inventory.staticGateIsNotLiveDump());
        assertFalse(inventory.liveDumpSkipReason().isBlank());
        assertTrue(inventory.leftoverDocsPresent());
        assertTrue(inventory.catalogOutsideSurface());
        assertTrue(inventory.productUsesAuthorizedExport());
        assertTrue(inventory.opsScriptsAreRunbook());
        assertTrue(inventory.leftoverRowsKept());
        assertFalse(inventory.liveDumpObserved());

        Map<String, String> report = G2OpsPgDumpPathHonesty.honestSkipTemplate(inventory);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        G2OpsPgDumpPathHonesty.verify(report);
        assertEquals(G2OpsPgDumpPathHonesty.G2_CLOSE, report.get("g2_close"));
        assertEquals("PASS", report.get("ops_pg_dump_outside_surfaceguard"));
        assertEquals("PASS", report.get("product_dump_uses_authorized_export"));
        assertEquals("PASS", report.get("ops_scripts_are_runbook"));
        assertEquals("false", report.get("ops_pg_dump_on_surfaceguard"));
        assertEquals("true", report.get("leftover_rows_kept"));
        assertEquals("true", report.get("static_gate_is_not_live_dump"));
        assertEquals("false", report.get("redone_155"));
        assertEquals("false", report.get("redone_137"));
        assertEquals("false", report.get("p0_m0_redone"));
        assertEquals("closed_unchanged", report.get("g18_status"));
    }

    @Test
    void honestyGateAcceptsHonestSkipFixture() {
        G2OpsPgDumpPathHonesty.verify(G2OpsPgDumpPathHonesty.parseReport(fixture("honest-skip.env")));
    }

    @Test
    void honestyGateRejectsFakeLiveDumpPass() {
        Map<String, String> fake = new HashMap<>(
                G2OpsPgDumpPathHonesty.parseReport(fixture("fake-live-dump-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G2OpsPgDumpPathHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("live_product_db_pg_dump"));
    }

    @Test
    void honestyGateRejectsG2CloseClaim() {
        Map<String, String> fake = new HashMap<>(
                G2OpsPgDumpPathHonesty.parseReport(fixture("fake-g2-close.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G2OpsPgDumpPathHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("g2_close"));
    }

    @Test
    void honestyGateRejectsOpsOnSurfaceAndSiblingRedos() {
        IllegalArgumentException onSurface = assertThrows(IllegalArgumentException.class,
                () -> G2OpsPgDumpPathHonesty.verify(
                        G2OpsPgDumpPathHonesty.parseReport(fixture("fake-ops-on-surface.env"))));
        assertTrue(onSurface.getMessage().contains("ops_pg_dump_on_surfaceguard")
                || onSurface.getMessage().contains("ops_pg_dump_outside_surfaceguard"));

        Map<String, String> fake = new HashMap<>(
                G2OpsPgDumpPathHonesty.parseReport(fixture("honest-skip.env")));
        fake.put("redone_155", "true");
        IllegalArgumentException redo155 = assertThrows(IllegalArgumentException.class,
                () -> G2OpsPgDumpPathHonesty.verify(fake));
        assertTrue(redo155.getMessage().contains("redone_155"));

        fake.put("redone_155", "false");
        fake.put("redone_137", "true");
        IllegalArgumentException redo137 = assertThrows(IllegalArgumentException.class,
                () -> G2OpsPgDumpPathHonesty.verify(fake));
        assertTrue(redo137.getMessage().contains("redone_137"));
    }

    @Test
    void docsReadmeKeepsAllSiblingLeftoverRows() throws IOException {
        String text = readExisting(repoFile("docs/README.md"));
        for (String row : G2OpsPgDumpPathHonesty.SIBLING_README_ROWS) {
            assertTrue(text.contains(row), "missing leftover row " + row);
        }
        assertTrue(text.contains("[g2-ops-pg-dump](./g2-ops-pg-dump.md)"));
        assertTrue(text.contains("[g2-ops-pg-dump-honesty](./g2-ops-pg-dump-honesty.md)"));
        assertTrue(text.contains("[g15-native-bridge-honesty](./g15-native-bridge-honesty.md)"));
        assertTrue(text.contains("[g10-identity-drift-honesty](./g10-identity-drift-honesty.md)"));
        assertTrue(text.contains("[g1-fieldpolicy-mask-second-auth-honesty](./g1-fieldpolicy-mask-second-auth-honesty.md)"));
        assertFalse(text.toLowerCase(Locale.ROOT).contains("product g2 closed"));
        assertFalse(text.contains("产品级 G2 已关闭"));

        String claims = readExisting(repoFile("docs/planning/团队领取任务清单_V1.1.md"));
        assertTrue(claims.contains("g2-ops-pg-dump.md"));
        assertTrue(claims.contains("g2-ops-pg-dump-honesty.md"));
        assertTrue(claims.contains("不宣称产品 G2 关闭"));
        assertTrue(claims.contains("#155 运维 pg_dump leftover"));
        assertTrue(claims.contains("leftover honesty beyond #155")
                || claims.contains("不重做 #137/#144/#150/#155"));
        assertFalse(claims.contains("产品 G2 已关闭"));
    }

    private static Path fixture(String name) {
        Path first = Path.of("scripts/g2-ops-pg-dump-honesty/fixtures/" + name);
        if (first.toFile().isFile()) {
            return first;
        }
        return Path.of("../scripts/g2-ops-pg-dump-honesty/fixtures/" + name);
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
