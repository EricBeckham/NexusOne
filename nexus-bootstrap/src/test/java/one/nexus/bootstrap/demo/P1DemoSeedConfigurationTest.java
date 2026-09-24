package one.nexus.bootstrap.demo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

class P1DemoSeedConfigurationTest {
    private final P1DemoSeedConfiguration configuration = new P1DemoSeedConfiguration();

    @Test
    void isInertWhenTheDemoSwitchIsOff() throws Exception {
        var env = new MockEnvironment().withProperty(DemoProfileGate.ENABLED, "false");
        var runner = configuration.p1DemoSeed(null, env, null);
        assertDoesNotThrow(() -> runner.run((ApplicationArguments) null));
    }

    @Test
    void rejectsRemoteAndUnspecifiedDatabasesBeforeWriting() {
        var jdbc = org.mockito.Mockito.mock(JdbcClient.class);
        var transaction = org.mockito.Mockito.mock(TransactionTemplate.class);
        var remote = new MockEnvironment()
                .withProperty(DemoProfileGate.ENABLED, "true")
                .withProperty(DemoProfileGate.ENVIRONMENT, "local")
                .withProperty("spring.datasource.url", "jdbc:postgresql://db.example.test/nexusone");
        var unspecified = new MockEnvironment()
                .withProperty(DemoProfileGate.ENABLED, "true")
                .withProperty(DemoProfileGate.ENVIRONMENT, "local");

        assertThrows(IllegalStateException.class,
                () -> configuration.p1DemoSeed(jdbc, remote, transaction).run((ApplicationArguments) null));
        assertThrows(IllegalStateException.class,
                () -> configuration.p1DemoSeed(jdbc, unspecified, transaction).run((ApplicationArguments) null));
        org.mockito.Mockito.verifyNoInteractions(jdbc, transaction);
    }
}
