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
import one.nexus.crypto.JdbcEncryptedSecretAdapter;
import one.nexus.crypto.LocalThenEnvSecretPort;
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
            "one.nexus.p4.market",
            "one.nexus.p5",
            "one.nexus.cache",
            "one.nexus.crypto",
            "one.nexus.expression",
            "one.nexus.data"
        },
        importOptions = ImportOption.DoNotIncludeTests.class)
class QueryCompilerBypassArchTest {
    static final ArchRule NO_JDBC = noClasses()
            .that().resideOutsideOfPackages(
                    "one.nexus.p4.market.infra.jdbc..",
                    "one.nexus.p5.connect.infra.jdbc..")
            .and().doNotBelongToAnyOf(JdbcEncryptedSecretAdapter.class, LocalThenEnvSecretPort.class)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("java.sql..", "javax.sql..", "jakarta.sql..", "org.springframework.jdbc..")
            .because("tenant-table DML goes through QueryCompiler / IsolationKernel.jdbc(); "
                    + "platform.secret_ciphertext JDBC is limited to the durable SecretPort; "
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
