package one.nexus.permission;

import java.time.Duration;
import one.nexus.audit.AuditPort;
import one.nexus.p2.IdentityModule;
import one.nexus.permission.engine.PolicySnapshotPoller;
import one.nexus.permission.engine.SnapshotManager;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.persist.InMemoryPolicyRepository;
import one.nexus.permission.persist.JdbcPolicyRepository;
import one.nexus.permission.persist.PolicyRepository;
import one.nexus.permission.redis.InMemoryPolicyInvalidation;
import one.nexus.permission.redis.PolicyInvalidationPort;
import one.nexus.permission.redis.Redis32Client;
import one.nexus.permission.redis.RedisPolicyInvalidation;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(PermissionConfiguration.PermissionProperties.class)
public class PermissionConfiguration {

    @Bean
    PolicyRepository policyRepository(PermissionProperties properties) {
        if ("jdbc".equalsIgnoreCase(properties.persistence())) {
            DataSource dataSource = DataSourceBuilder.create()
                    .url(properties.jdbc().url())
                    .username(properties.jdbc().username())
                    .password(properties.jdbc().password())
                    .driverClassName("org.postgresql.Driver")
                    .build();
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .schemas("iam")
                    .defaultSchema("iam")
                    .createSchemas(true)
                    .load()
                    .migrate();
            return new JdbcPolicyRepository(new JdbcTemplate(dataSource));
        }
        return new InMemoryPolicyRepository();
    }

    @Bean(destroyMethod = "shutdown")
    PolicyInvalidationPort policyInvalidationPort(PermissionProperties properties) {
        if (properties.redis() != null && properties.redis().enabled()) {
            Redis32Client client = new Redis32Client(properties.redis().host(), properties.redis().port());
            return new RedisPolicyInvalidation(client);
        }
        return new InMemoryPolicyInvalidation();
    }

    @Bean
    PermissionModule permissionModule(
            PolicyRepository repository,
            IdentityModule identity,
            PolicyInvalidationPort invalidation,
            ObjectProvider<AuditPort> audits,
            PermissionProperties properties
    ) {
        PermissionModule module = PermissionModule.create(
                repository,
                identity.repository(),
                invalidation,
                audits.getIfAvailable(),
                properties.snapshotLoadTimeout());
        invalidation.startListening();
        return module;
    }

    @Bean
    @ConditionalOnProperty(name = "nexus.permission.seed-tenant-member-grants", havingValue = "true")
    TenantMemberGrantSeeder tenantMemberGrantSeeder(PermissionModule module) {
        return new TenantMemberGrantSeeder(module);
    }

    @Bean(destroyMethod = "close")
    PolicySnapshotPoller policySnapshotPoller(PermissionModule module, PermissionProperties properties) {
        PolicySnapshotPoller poller = new PolicySnapshotPoller(module.snapshots(), properties.snapshotPollInterval());
        poller.start();
        return poller;
    }

    @ConfigurationProperties(prefix = "nexus.permission")
    public record PermissionProperties(
            String persistence,
            Jdbc jdbc,
            Redis redis,
            Duration snapshotPollInterval,
            Duration snapshotLoadTimeout
    ) {
        public PermissionProperties {
            if (persistence == null) {
                persistence = "memory";
            }
            if (jdbc == null) {
                jdbc = new Jdbc("jdbc:postgresql://127.0.0.1:5432/nexusone", "postgres", "");
            }
            if (redis == null) {
                redis = new Redis(false, "127.0.0.1", 6379);
            }
            if (snapshotPollInterval == null) {
                snapshotPollInterval = Duration.ofSeconds(2);
            }
            if (snapshotLoadTimeout == null || snapshotLoadTimeout.isZero() || snapshotLoadTimeout.isNegative()) {
                snapshotLoadTimeout = SnapshotManager.DEFAULT_LOAD_TIMEOUT;
            }
        }

        public record Jdbc(String url, String username, String password) {}

        public record Redis(boolean enabled, String host, int port) {
            public Redis {
                if (host == null || host.isBlank()) {
                    host = "127.0.0.1";
                }
                if (port <= 0) {
                    port = 6379;
                }
            }
        }
    }
}
