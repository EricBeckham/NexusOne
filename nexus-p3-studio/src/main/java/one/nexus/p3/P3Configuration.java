package one.nexus.p3;

import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import one.nexus.isolation.IsolationKernel;
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
    IsolationKernel isolationKernel() {
        return IsolationKernel.create(
                new TableRegistry(),
                Set.of("p3.schema"),
                new CollectingAuditSink());
    }

    /**
     * Fallback when P1 {@code QuotaService} is absent. {@code @ConditionalOnMissingBean}
     * belongs on {@code @Bean} methods — the same annotation on a {@code @Component}
     * is evaluated during component scan and can skip both the fallback and the P1 hook.
     */
    @Bean
    @ConditionalOnMissingBean(QuotaHook.class)
    QuotaHook quotaHook() {
        return new NoopQuotaHook();
    }

    /** Fallback list/detail filter: {@code tenant_id = ? AND (TRUE)}. */
    @Bean
    @ConditionalOnMissingBean(DataScopeHook.class)
    DataScopeHook dataScopeHook() {
        return new TenantOnlyDataScopeHook();
    }
}
