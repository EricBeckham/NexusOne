package one.nexus.bootstrap.demo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class P2DemoSeedConfigurationTest {
    private final P2DemoSeedConfiguration configuration = new P2DemoSeedConfiguration();

    @Test
    void isInertWhenTheDemoSwitchIsOff() throws Exception {
        var env = new MockEnvironment().withProperty(DemoProfileGate.ENABLED, "false");
        assertDoesNotThrow(() -> configuration.p2DemoSeed(null, env, null).run((ApplicationArguments) null));
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
                () -> configuration.p2DemoSeed(jdbc, env, transaction).run((ApplicationArguments) null));
        org.mockito.Mockito.verifyNoInteractions(jdbc, transaction);
    }

    @Test
    void writesOnlyIdempotentTenantScopedRowsWhenEnabled() throws Exception {
        var jdbc = org.mockito.Mockito.mock(JdbcClient.class);
        var statement = org.mockito.Mockito.mock(JdbcClient.StatementSpec.class);
        var transaction = org.mockito.Mockito.mock(TransactionTemplate.class);
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
                .withProperty("spring.datasource.url", "jdbc:postgresql://127.0.0.1:5432/nexusone");

        configuration.p2DemoSeed(jdbc, env, transaction).run((ApplicationArguments) null);

        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.times(14)).sql(sql.capture());
        for (String statementSql : sql.getAllValues()) {
            assertTrue(statementSql.contains("ON CONFLICT"), "seed insert must be idempotent");
            if (!statementSql.contains("iam.iam_global_account")) {
                assertTrue(statementSql.contains(":tenant"), "tenant-owned seed rows must be scoped to the demo tenant");
            }
        }
    }
}
