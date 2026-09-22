package one.nexus.bootstrap.audit;

import one.nexus.audit.AuditHotRetentionJob;
import one.nexus.audit.AuditPort;
import one.nexus.task.TaskHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the 90-day hot-retention scan stub. Does not schedule Isolated
 * Quartz at boot (that API requires a tenant membership and would risk product
 * start). The bean is callable; the Task handler is {@code audit.retention.hot-scan}.
 */
@Configuration
public class AuditRetentionConfiguration {
    @Bean
    @ConditionalOnBean(AuditPort.class)
    AuditHotRetentionJob auditHotRetentionJob(AuditPort auditPort) {
        return new AuditHotRetentionJob(auditPort);
    }

    @Bean
    @ConditionalOnBean(AuditHotRetentionJob.class)
    TaskHandler auditHotRetentionHandler(AuditHotRetentionJob job) {
        return new AuditHotRetentionHandler(job);
    }
}
