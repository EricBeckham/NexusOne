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
        @SuppressWarnings("unchecked")
        JdbcClient.MappedQuerySpec<java.util.UUID> departmentQuery =
                org.mockito.Mockito.mock(JdbcClient.MappedQuerySpec.class);
        org.mockito.Mockito.when(statement.query(java.util.UUID.class)).thenReturn(departmentQuery);
        org.mockito.Mockito.when(departmentQuery.optional()).thenReturn(java.util.Optional.of(java.util.UUID.randomUUID()));
        @SuppressWarnings("unchecked")
        JdbcClient.MappedQuerySpec<String> pathQuery = org.mockito.Mockito.mock(JdbcClient.MappedQuerySpec.class);
        org.mockito.Mockito.when(statement.query(String.class)).thenReturn(pathQuery);
        org.mockito.Mockito.when(pathQuery.single()).thenReturn("/existing-parent/");
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
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.atLeast(22)).sql(sql.capture());
        for (String statementSql : sql.getAllValues()) {
            if (statementSql.contains("INSERT")) {
                assertTrue(statementSql.contains("ON CONFLICT"), "seed insert must be idempotent");
            }
            if (!statementSql.contains("iam.iam_global_account")) {
                assertTrue(statementSql.contains(":tenant") || statementSql.contains(":platform"),
                        "tenant-owned seed rows must be scoped");
            }
            assertTrue(!statementSql.contains("'TENANT_ADMIN'") && !statementSql.contains("'PLATFORM_ADMIN'"),
                    statementSql);
        }
        String joined = String.join("\n", sql.getAllValues());
        assertTrue(joined.contains("'demo_tenant_admin'"));
        assertTrue(joined.contains("'demo_platform_ops'"));
        assertTrue(joined.contains("'demo_member'"));
        assertTrue(joined.contains("'nexus-demo-platform'"));
        assertTrue(joined.contains("'nexus-demo-tenant-admin'"));
        assertTrue(joined.contains("'nexus-demo-member'"));
        assertTrue(joined.contains("SUSPENDED"), "one account must not stay ACTIVE on both platform and demo_acme");
    }
}
