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

class P5DemoSeedConfigurationTest {
    private final P5DemoSeedConfiguration configuration = new P5DemoSeedConfiguration();

    @Test
    void isInertWhenDemoSwitchIsOff() throws Exception {
        var env = new MockEnvironment().withProperty(DemoProfileGate.ENABLED, "false");
        assertDoesNotThrow(() -> configuration.p5DemoSeed(null, env, null).run((ApplicationArguments) null));
    }

    @Test
    void refusesRemoteDatabaseBeforeAnyWrite() {
        var jdbc = org.mockito.Mockito.mock(JdbcClient.class);
        var transaction = org.mockito.Mockito.mock(TransactionTemplate.class);
        var env = new MockEnvironment().withProperty(DemoProfileGate.ENABLED, "true")
                .withProperty(DemoProfileGate.ENVIRONMENT, "local")
                .withProperty("spring.datasource.url", "jdbc:postgresql://db.example.test/nexusone");
        assertThrows(IllegalStateException.class,
                () -> configuration.p5DemoSeed(jdbc, env, transaction).run((ApplicationArguments) null));
        org.mockito.Mockito.verifyNoInteractions(jdbc, transaction);
    }

    @Test
    void seedsOnlyDisabledClientAndDemoSummariesWithoutWebhookOrSecretMaterial() throws Exception {
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

        configuration.p5DemoSeed(jdbc, env, transaction).run((ApplicationArguments) null);

        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.times(7)).sql(sql.capture());
        var p5Statements = sql.getAllValues().stream().filter(query -> query.contains("integration.")).toList();
        assertTrue(p5Statements.size() == 3, "expected three P5 summary inserts");
        for (String query : p5Statements) {
            assertTrue(query.contains("ON CONFLICT"), "seed insert must be idempotent");
            assertTrue(!query.contains("webhook_subscription") && !query.contains("webhook_delivery"),
                    "P5 seed does not create callable webhook configuration");
            assertTrue(!query.contains("secret=") && !query.contains("webhook_signing_secret"),
                    "P5 seed must not contain usable secrets");
        }
        assertTrue(p5Statements.get(0).contains("'DEMO-REVOKED'"));
        assertTrue(p5Statements.get(0).contains("FALSE"));
        assertTrue(p5Statements.get(0).contains("decode(repeat('00', 32), 'hex')"));
    }
}
