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

class P4DemoSeedConfigurationTest {
    private final P4DemoSeedConfiguration configuration = new P4DemoSeedConfiguration();

    @Test
    void isInertWhenDemoSwitchIsOff() throws Exception {
        var env = new MockEnvironment().withProperty(DemoProfileGate.ENABLED, "false");
        assertDoesNotThrow(() -> configuration.p4DemoSeed(null, env, null).run((ApplicationArguments) null));
    }

    @Test
    void refusesRemoteDatabaseBeforeAnyWrite() {
        var jdbc = org.mockito.Mockito.mock(JdbcClient.class);
        var transaction = org.mockito.Mockito.mock(TransactionTemplate.class);
        var env = new MockEnvironment().withProperty(DemoProfileGate.ENABLED, "true")
                .withProperty(DemoProfileGate.ENVIRONMENT, "local")
                .withProperty("spring.datasource.url", "jdbc:postgresql://db.example.test/nexusone");
        assertThrows(IllegalStateException.class,
                () -> configuration.p4DemoSeed(jdbc, env, transaction).run((ApplicationArguments) null));
        org.mockito.Mockito.verifyNoInteractions(jdbc, transaction);
    }

    @Test
    void seedsOnlyIdempotentPublishedCatalogRows() throws Exception {
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
        var env = new MockEnvironment().withProperty(DemoProfileGate.ENABLED, "true")
                .withProperty(DemoProfileGate.ENVIRONMENT, "local")
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/nexusone");

        configuration.p4DemoSeed(jdbc, env, transaction).run((ApplicationArguments) null);

        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.times(3)).sql(sql.capture());
        for (String query : sql.getAllValues()) {
            assertTrue(query.contains("ON CONFLICT"), "seed insert must be idempotent");
            assertTrue(query.contains("market."), "only P4 catalog tables should be written");
            assertTrue(!query.contains("subscription") && !query.contains("installation"),
                    "demo rows must not grant a tenant subscription or installation");
        }
        assertTrue(sql.getAllValues().get(1).contains("'PUBLISHED'"));
        String sku = sql.getAllValues().get(2);
        assertTrue(sku.contains("\"projects\""), "sku entitlements must be a map of EntitlementSpec");
        assertTrue(sku.contains("\"enabled\":true") && sku.contains("\"quota\":20"));
        assertTrue(!sku.contains("\"demo\":true,\"entitlements\""),
                "wrapped demo entitlements cannot be read by the catalog");
    }
}
