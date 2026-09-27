package one.nexus.p4.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import one.nexus.p1.port.IsolationGuard;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** DPO-X4 / #647: real Boot Jackson 3 autoconfig; no fake Jackson 2 bean. No DB. */
class PrintMappingMapperBootContractTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withPropertyValues("spring.jackson.property-naming-strategy=SNAKE_CASE")
            .withBean(IsolationGuard.class, () -> mock(IsolationGuard.class))
            .withBean(MarketRepository.class, () -> mock(MarketRepository.class));

    @Test
    void printServiceStartsAndLoadsCamelCaseContractWithOnlyProductJackson3() {
        runner.withUserConfiguration(PrintConfig.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(PrintMappingService.class)
                    .hasSingleBean(tools.jackson.databind.ObjectMapper.class)
                    .doesNotHaveBean(com.fasterxml.jackson.databind.ObjectMapper.class);
            // Guard mock is neutral: this tests initialization, not authorization.
            var contract = context.getBean(PrintMappingService.class).contract();
            assertThat(contract.contractVersion()).isEqualTo("1.0");
            assertThat(contract.builtinTemplate().code()).isEqualTo("builtin.approval-form.a4.v1");
            assertThat(contract.paperSizes()).isNotEmpty();
            var wire = context.getBean(tools.jackson.databind.ObjectMapper.class).valueToTree(contract);
            assertThat(wire.has("contract_version")).isTrue();
            assertThat(wire.has("contractVersion")).isFalse();
        });
    }

    @Test
    void historicalJackson2InjectionWouldFailInsteadOfBeingHiddenByTestBean() {
        runner.withUserConfiguration(HistoricalMissingMapper.class).run(context ->
                assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @Import({PrintMappingService.class, InMemoryPrintMappingStore.class})
    static class PrintConfig {}

    @Configuration(proxyBeanMethods = false)
    static class HistoricalMissingMapper {
        @Bean Object regressedPrintDependency(com.fasterxml.jackson.databind.ObjectMapper missing) { return missing; }
    }
}
