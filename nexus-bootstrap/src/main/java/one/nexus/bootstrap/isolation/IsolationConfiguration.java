package one.nexus.bootstrap.isolation;

import one.nexus.audit.AuditPort;
import one.nexus.isolation.api.IsolationAuditSink;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IsolationConfiguration {
    @Bean
    @ConditionalOnBean(AuditPort.class)
    @ConditionalOnMissingBean
    IsolationAuditSink isolationAuditSink(AuditPort auditPort) {
        return new AuditPortIsolationSink(auditPort);
    }
}
