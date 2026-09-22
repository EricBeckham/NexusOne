package one.nexus.permission.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import one.nexus.permission.surface.H5PageSurfaceCatalog;
import org.junit.jupiter.api.Test;

/**
 * G2 leftover gate: unclassified /m routes and fake G2 close fail;
 * product router + inventory stay honest. Does not redo #116/#150/#144/#137.
 */
class H5PageSurfaceHonestyGateTest {
    private final H5PageSurfaceHonestyGate gate = new H5PageSurfaceHonestyGate();
    private final Path fixtures = Path.of("src/test/resources/h5-every-page-fixtures");

    @Test
    void unclassifiedRouteFailsTheGate() throws Exception {
        List<H5PageSurfaceHonestyGate.Violation> violations = gate.scan(fixtures.resolve("negative/unclassified"));
        assertFalse(violations.isEmpty(), "expected unclassified /m/p3/reports to fail");
        assertTrue(
                violations.stream().anyMatch(v -> H5PageSurfaceHonestyGate.UNCLASSIFIED_ROUTE.equals(v.rule())
                        && v.detail().contains("/m/p3/reports")),
                violations::toString);
    }

    @Test
    void fakeG2CloseFailsTheGate() throws Exception {
        List<H5PageSurfaceHonestyGate.Violation> violations = gate.scan(fixtures.resolve("negative/fake-close"));
        assertFalse(violations.isEmpty(), "expected g2_close=CLOSED to fail");
        assertTrue(
                violations.stream().anyMatch(v -> H5PageSurfaceHonestyGate.G2_CLOSE_CLAIMED.equals(v.rule())),
                violations::toString);
    }

    @Test
    void commentOnlyHonestyNotePasses() throws Exception {
        List<H5PageSurfaceHonestyGate.Violation> violations = gate.scan(fixtures.resolve("positive"));
        assertTrue(violations.isEmpty(), violations::toString);
    }

    @Test
    void catalogKeepsCoveredAndOpenAndDoesNotClaimG2() {
        assertEquals("NOT_CLAIMED", H5PageSurfaceCatalog.G2_CLOSE);
        assertTrue(H5PageSurfaceCatalog.BOUNDARY.contains("not on SurfaceGuard"));
        assertFalse(H5PageSurfaceCatalog.coveredPaths().isEmpty());
        assertFalse(H5PageSurfaceCatalog.openPaths().isEmpty());
        assertTrue(H5PageSurfaceCatalog.coveredPaths().contains("/m/p3/lists"));
        assertTrue(H5PageSurfaceCatalog.coveredPaths().contains("/m/p3/forms/:id"));
        assertTrue(H5PageSurfaceCatalog.coveredPaths().contains("/m/p3/todos"));
        assertTrue(H5PageSurfaceCatalog.coveredPaths().contains("/m/p3/query"));
        assertTrue(H5PageSurfaceCatalog.openPaths().contains("/m/p3/home"));
        assertTrue(H5PageSurfaceCatalog.openPaths().contains("/m/p3/files"));
        assertTrue(H5PageSurfaceCatalog.openPaths().contains("/m/p3/notifications"));
        assertTrue(H5PageSurfaceCatalog.openPaths().contains("/m/login"));
        assertFalse(H5PageSurfaceCatalog.coveredPaths().contains("/m/p3/files"));
    }

    @Test
    void parserReadsRouterMobilePaths() {
        String router =
                """
                { path: "/m", redirect: "/m/p3/home" },
                { path: "/m/p3/lists", component: () => import("./mobile/products/p3-studio/pages/ListsPage.vue") },
                { path: "/p3/ledgers", component: () => import("./views/studio/LedgerListView.vue") },
                """;
        Map<String, String> routes = H5PageSurfaceHonestyGate.parseRouter(router);
        assertEquals("", routes.get("/m"));
        assertEquals("ListsPage.vue", routes.get("/m/p3/lists"));
        assertFalse(routes.containsKey("/p3/ledgers"));
    }

    @Test
    void productRouterAndInventoryPass() throws Exception {
        Path repo = H5PageSurfaceHonestyGate.findRepoRoot(Path.of(System.getProperty("user.dir")));
        List<H5PageSurfaceHonestyGate.Violation> violations = gate.scanProduct(repo);
        assertTrue(violations.isEmpty(), () -> "H5 every-page honesty failed:\n" + format(violations));
    }

    private static String format(List<H5PageSurfaceHonestyGate.Violation> violations) {
        StringBuilder out = new StringBuilder();
        for (H5PageSurfaceHonestyGate.Violation violation : violations) {
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
