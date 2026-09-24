package one.nexus.bootstrap.demo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import one.nexus.p3.port.QuotaHook;

class P3DemoSeedConfigurationTest {
    private final P3DemoSeedConfiguration configuration = new P3DemoSeedConfiguration();

    @Test
    void isInertWhenTheDemoSwitchIsOff() throws Exception {
        var env = new MockEnvironment().withProperty(DemoProfileGate.ENABLED, "false");
        assertDoesNotThrow(() -> configuration.p3DemoSeed(null, env, null, null).run((ApplicationArguments) null));
    }

    @Test
    void refusesAProductionShapedDatabaseBeforeAnySeedWrite() {
        var jdbc = org.mockito.Mockito.mock(JdbcClient.class);
        var transaction = org.mockito.Mockito.mock(TransactionTemplate.class);
        var env = new MockEnvironment()
                .withProperty(DemoProfileGate.ENABLED, "true")
                .withProperty(DemoProfileGate.ENVIRONMENT, "local")
                .withProperty("spring.datasource.url", "jdbc:postgresql://db.example.test/nexusone");
        assertThrows(IllegalStateException.class,
                () -> configuration.p3DemoSeed(jdbc, env, transaction, null).run((ApplicationArguments) null));
        org.mockito.Mockito.verifyNoInteractions(jdbc, transaction);
    }

    @Test
    void seedsOnlyTenantScopedIdempotentMetadataAndNoPhysicalDdl() throws Exception {
        var jdbc = org.mockito.Mockito.mock(JdbcClient.class);
        var statement = org.mockito.Mockito.mock(JdbcClient.StatementSpec.class);
        var transaction = org.mockito.Mockito.mock(TransactionTemplate.class);
        var quotas = org.mockito.Mockito.mock(QuotaHook.class);
        org.mockito.Mockito.when(jdbc.sql(org.mockito.ArgumentMatchers.anyString())).thenReturn(statement);
        org.mockito.Mockito.when(statement.param(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(statement);
        org.mockito.Mockito.when(statement.update()).thenReturn(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(org.mockito.Mockito.mock(TransactionStatus.class));
            return null;
        }).when(transaction).executeWithoutResult(org.mockito.ArgumentMatchers.any());
        var env = new MockEnvironment()
                .withProperty(DemoProfileGate.ENABLED, "true")
                .withProperty(DemoProfileGate.ENVIRONMENT, "local")
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/nexusone");

        configuration.p3DemoSeed(jdbc, env, transaction, quotas).run((ApplicationArguments) null);

        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.times(8)).sql(sql.capture());
        var p3Statements = sql.getAllValues().stream()
                .filter(statementSql -> statementSql.contains("business_meta."))
                .toList();
        assertTrue(p3Statements.size() == 4, "expected four P3 metadata inserts");
        for (String statementSql : p3Statements) {
            assertTrue(statementSql.contains("ON CONFLICT"), "seed insert must be idempotent");
            assertTrue(statementSql.contains(":tenant"), "P3 rows must use the fixed demo tenant");
            assertTrue(!statementSql.contains("CREATE TABLE"), "P3 seed must not create a physical table");
        }
        org.mockito.Mockito.verify(quotas).consumeApp(P3DemoSeedConfiguration.TENANT_ID,
                "p3.app:" + P3DemoSeedConfiguration.TENANT_ID + ":demo_studio");
        org.mockito.Mockito.verify(quotas).consumeCustomLedger(P3DemoSeedConfiguration.TENANT_ID,
                "p3.ledger:" + P3DemoSeedConfiguration.TENANT_ID + ":project");
    }
}
