package one.nexus.bootstrap.isolation;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import one.nexus.bootstrap.isolation.bypass.RawTenantSqlFixture;
import org.junit.jupiter.api.Test;

/**
 * ArchUnit complement to {@code QueryCompilerBypassGate}: application / API /
 * domain / reserved product packages must not depend on JDBC.
 */
@AnalyzeClasses(
        packages = {
            "one.nexus.p3.application",
            "one.nexus.p3.api",
            "one.nexus.p3.domain",
            "one.nexus.p3.path",
            "one.nexus.p3.studio.api",
            "one.nexus.p4",
            "one.nexus.p5",
            "one.nexus.cache",
            "one.nexus.crypto",
            "one.nexus.expression",
            "one.nexus.data"
        },
        importOptions = ImportOption.DoNotIncludeTests.class)
class QueryCompilerBypassArchTest {
    static final ArchRule NO_JDBC = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("java.sql..", "javax.sql..", "jakarta.sql..", "org.springframework.jdbc..")
            .because("tenant-table DML goes through QueryCompiler / IsolationKernel.jdbc(); "
                    + "see docs/g1-querycompiler-bypass-ci.md");

    @ArchTest
    static final ArchRule applicationLayersMustNotUseJdbc = NO_JDBC;

    @Test
    void intentionalViolationFailsArchUnit() {
        JavaClasses imported = new ClassFileImporter().importClasses(RawTenantSqlFixture.class);
        AssertionError error = assertThrows(AssertionError.class, () -> NO_JDBC.check(imported));
        assertTrue(
                error.getMessage().contains("RawTenantSqlFixture")
                        || error.getMessage().contains("java.sql"),
                error::getMessage);
    }
}
