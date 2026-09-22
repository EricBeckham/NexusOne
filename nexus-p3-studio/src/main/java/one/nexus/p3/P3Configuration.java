package one.nexus.p3;

import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.IsolationAuditSink;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.p3.infra.permission.TenantOnlyDataScopeHook;
import one.nexus.p3.infra.quota.NoopQuotaHook;
import one.nexus.p3.port.DataScopeHook;
import one.nexus.p3.port.QuotaHook;

@Configuration
@EnableConfigurationProperties(P3Properties.class)
public class P3Configuration {
    @Bean
    @ConditionalOnMissingBean
    IsolationKernel isolationKernel(ObjectProvider<IsolationAuditSink> sinks) {
        return IsolationKernel.create(
                new TableRegistry(),
                Set.of("p3.schema"),
                sinks.getIfAvailable(CollectingAuditSink::new));
    }

    /**
     * Always-on fallback. P1 {@link one.nexus.p3.infra.quota.P1QuotaHook} is
     * {@code @Primary} when {@code QuotaService} exists. Do not use
     * {@code @ConditionalOnMissingBean} here: the P1 {@code @Component} definition
     * is visible during condition evaluation even when QuotaService is absent.
     */
    @Bean
    QuotaHook quotaHook() {
        return new NoopQuotaHook();
    }

    /** Fallback list/detail filter: {@code tenant_id = ? AND (TRUE)}. */
    @Bean
    DataScopeHook dataScopeHook() {
        return new TenantOnlyDataScopeHook();
    }
}
