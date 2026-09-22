package one.nexus.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import one.nexus.event.jdbc.JdbcOutboxStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class EventCoreConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EventCoreConfiguration.class));

    @Test
    void registersJdbcOutboxPortWhenDataSourceExists() {
        runner.withUserConfiguration(StubDataSourceConfig.class)
                .withPropertyValues("nexus.execution.dispatcher-enabled=false")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(JdbcOutboxStore.class);
                    assertThat(ctx).hasSingleBean(OutboxPort.class);
                    assertThat(ctx.getBean(OutboxPort.class)).isSameAs(ctx.getBean(JdbcOutboxStore.class));
                    assertThat(ctx).hasSingleBean(OutboxDispatcher.class);
                });
    }

    @Test
    void registersJdbcOutboxPortWhenMultipleClockBeansExist() {
        runner.withUserConfiguration(StubDataSourceConfig.class, TwoClocksConfig.class)
                .withPropertyValues("nexus.execution.dispatcher-enabled=false")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(OutboxPort.class);
                    assertThat(ctx.getBean(OutboxPort.class)).isInstanceOf(JdbcOutboxStore.class);
                });
    }

    @Test
    void doesNotRegisterOutboxPortWithoutDataSource() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).doesNotHaveBean(OutboxPort.class);
            assertThat(ctx).doesNotHaveBean(JdbcOutboxStore.class);
        });
    }

    @Test
    void autoConfigurationImportsListsEventCoreConfiguration() throws Exception {
        try (var in = EventCoreConfiguration.class.getClassLoader().getResourceAsStream(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(in).isNotNull();
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8).strip())
                    .isEqualTo("one.nexus.event.EventCoreConfiguration");
        }
    }

    @Configuration
    static class StubDataSourceConfig {
        @Bean
        DataSource dataSource() {
            return mock(DataSource.class);
        }
    }

    @Configuration
    static class TwoClocksConfig {
        @Bean
        java.time.Clock clock() {
            return java.time.Clock.systemUTC();
        }

        @Bean
        java.time.Clock studioClock() {
            return java.time.Clock.systemUTC();
        }
    }
}
