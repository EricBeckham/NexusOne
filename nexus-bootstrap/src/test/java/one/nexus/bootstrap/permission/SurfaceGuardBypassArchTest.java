package one.nexus.bootstrap.permission;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import one.nexus.bootstrap.permission.bypass.RawSurfaceGuardBypassFixture;
import org.junit.jupiter.api.Test;

/**
 * ArchUnit complement to {@code SurfaceGuardBypassGate}: application / API /
 * domain / path / reserved product packages must not depend on
 * {@code PermissionEngine} or {@code IsolatedExport}.
 */
@AnalyzeClasses(
        packages = {
            "one.nexus.p3.application",
            "one.nexus.p3.api",
            "one.nexus.p3.domain",
            "one.nexus.p3.path",
            "one.nexus.p3.studio.api",
            "one.nexus.p4",
            "one.nexus.p5"
        },
        importOptions = ImportOption.DoNotIncludeTests.class)
class SurfaceGuardBypassArchTest {
    static final ArchRule NO_RAW_ENGINE = noClasses()
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("one.nexus.permission.engine.PermissionEngine")
            .because("product surfaces interpret Decision through SurfaceGuard; "
                    + "see docs/g2-surfaceguard-bypass-ci.md");

    static final ArchRule NO_RAW_EXPORT = noClasses()
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("one.nexus.isolation.boundary.IsolatedExport")
            .because("product export goes through AuthorizedExport then IsolatedExport; "
                    + "see docs/g2-surfaceguard-bypass-ci.md");

    @ArchTest
    static final ArchRule applicationLayersMustNotCallPermissionEngine = NO_RAW_ENGINE;

    @ArchTest
    static final ArchRule applicationLayersMustNotCallIsolatedExport = NO_RAW_EXPORT;

    @Test
    void intentionalViolationFailsArchUnit() {
        JavaClasses imported = new ClassFileImporter().importClasses(RawSurfaceGuardBypassFixture.class);
        AssertionError engine = assertThrows(AssertionError.class, () -> NO_RAW_ENGINE.check(imported));
        assertTrue(
                engine.getMessage().contains("RawSurfaceGuardBypassFixture")
                        || engine.getMessage().contains("PermissionEngine"),
                engine::getMessage);
        AssertionError export = assertThrows(AssertionError.class, () -> NO_RAW_EXPORT.check(imported));
        assertTrue(
                export.getMessage().contains("RawSurfaceGuardBypassFixture")
                        || export.getMessage().contains("IsolatedExport"),
                export::getMessage);
    }
}
