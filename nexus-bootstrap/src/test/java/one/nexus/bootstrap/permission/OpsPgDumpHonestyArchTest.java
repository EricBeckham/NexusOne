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
import one.nexus.bootstrap.permission.bypass.RawOpsPgDumpFixture;
import org.junit.jupiter.api.Test;

/**
 * ArchUnit note: product dump / report / AuthorizedExport packages must not
 * spawn {@code ProcessBuilder}. Ops {@code pg_dump} is out of SurfaceGuard.
 * See {@code docs/g2-ops-pg-dump.md}.
 */
@AnalyzeClasses(
        packages = {
            "one.nexus.p3.dump",
            "one.nexus.p3.report",
            "one.nexus.permission.surface"
        },
        importOptions = ImportOption.DoNotIncludeTests.class)
class OpsPgDumpHonestyArchTest {
    static final ArchRule NO_PROCESS_ON_PRODUCT_DUMP = noClasses()
            .should()
            .callConstructor(ProcessBuilder.class)
            .because("ops pg_dump is out of product SurfaceGuard; "
                    + "dump/export go through AuthorizedExport; see docs/g2-ops-pg-dump.md");

    @ArchTest
    static final ArchRule productDumpMustNotSpawnProcess = NO_PROCESS_ON_PRODUCT_DUMP;

    @Test
    void intentionalPgDumpProcessFailsArchUnit() {
        JavaClasses imported = new ClassFileImporter().importClasses(RawOpsPgDumpFixture.class);
        AssertionError error = assertThrows(AssertionError.class, () -> NO_PROCESS_ON_PRODUCT_DUMP.check(imported));
        assertTrue(
                error.getMessage().contains("RawOpsPgDumpFixture")
                        || error.getMessage().contains("ProcessBuilder"),
                error::getMessage);
    }
}
