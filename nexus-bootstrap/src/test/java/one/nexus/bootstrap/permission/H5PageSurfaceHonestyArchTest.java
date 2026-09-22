package one.nexus.bootstrap.permission;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import one.nexus.permission.surface.H5PageSurfaceCatalog;
import org.junit.jupiter.api.Test;

/**
 * ArchUnit note: #116 {@code H5SurfaceGuard} stays the SurfaceGuard twin.
 * This leftover only inventories covered vs open {@code /m/*} pages.
 * See {@code docs/g2-h5-every-page.md}.
 */
@AnalyzeClasses(
        packages = {"one.nexus.p3.infra.permission", "one.nexus.permission.surface"},
        importOptions = ImportOption.DoNotIncludeTests.class)
class H5PageSurfaceHonestyArchTest {
    static final ArchRule H5_GUARD_STILL_USES_SURFACE = classes()
            .that()
            .haveSimpleName("H5SurfaceGuard")
            .should()
            .dependOnClassesThat()
            .haveSimpleName("SurfaceGuard")
            .because("#116 twin stays; every-page leftover does not redo H5SurfaceGuard; "
                    + "see docs/g2-h5-every-page.md");

    @ArchTest
    static final ArchRule h5SurfaceGuardStaysTheTwin = H5_GUARD_STILL_USES_SURFACE;

    @Test
    void catalogKeepsOpenPagesAndDoesNotClaimG2() {
        assertEquals("NOT_CLAIMED", H5PageSurfaceCatalog.G2_CLOSE);
        assertFalse(H5PageSurfaceCatalog.openPaths().isEmpty());
        assertFalse(H5PageSurfaceCatalog.coveredPaths().isEmpty());
        assertTrue(H5PageSurfaceCatalog.coveredPaths().contains("/m/p3/lists"));
        assertTrue(H5PageSurfaceCatalog.openPaths().contains("/m/p3/files"));
        assertTrue(H5PageSurfaceCatalog.BOUNDARY.contains("not on SurfaceGuard"));
    }
}
