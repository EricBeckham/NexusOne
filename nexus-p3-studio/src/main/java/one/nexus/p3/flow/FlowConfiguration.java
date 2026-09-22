package one.nexus.p3.flow;

import java.util.Set;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.IsolationAuditSink;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.p2.IdentityModule;
import one.nexus.p3.flow.persist.JdbcProcessStore;
import one.nexus.permission.PermissionModule;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ConditionalOnProperty(name = "nexus.flow.engine")
public class FlowConfiguration {
    @Bean
    @ConditionalOnMissingBean
    IsolationKernel isolationKernel(ObjectProvider<IsolationAuditSink> sinks) {
        return IsolationKernel.create(
                new TableRegistry(),
                Set.of("flow.ops"),
                sinks.getIfAvailable(CollectingAuditSink::new));
    }

    @Bean
    JdbcProcessStore jdbcProcessStore(JdbcTemplate jdbcTemplate) {
        return new JdbcProcessStore(jdbcTemplate);
    }

    @Bean
    AssigneeResolver assigneeResolver(IdentityModule identity, PermissionModule permission) {
        return new AssigneeResolver(identity.repository(), permission.repository());
    }
}
