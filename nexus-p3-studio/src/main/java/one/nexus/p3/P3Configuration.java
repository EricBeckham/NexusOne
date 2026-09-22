package one.nexus.p3;

import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.query.TableRegistry;

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
}
