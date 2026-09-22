package one.nexus.bootstrap.audit;

import javax.sql.DataSource;
import one.nexus.audit.AuditPort;
import one.nexus.audit.InMemoryHashChainedAuditPort;
import one.nexus.audit.JdbcHashChainedAuditPort;
import one.nexus.audit.recovery.DryRunRecoveryPort;
import one.nexus.audit.recovery.RecoveryPort;
import one.nexus.crypto.CryptoPort;
import one.nexus.p1.infra.audit.JdbcAuditPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AuditConfiguration {
    @Bean
    AuditPort auditPort(
            CryptoPort cryptoPort,
            ObjectProvider<DataSource> dataSources,
            @Value("${nexus.audit.persistence:jdbc}") String persistence) {
        if ("jdbc".equalsIgnoreCase(persistence)) {
            DataSource dataSource = dataSources.getIfAvailable();
            if (dataSource == null) {
                throw new IllegalStateException("nexus.audit.persistence=jdbc requires a DataSource");
            }
            return new JdbcHashChainedAuditPort(dataSource, cryptoPort);
        }
        return new InMemoryHashChainedAuditPort(cryptoPort);
    }

    @Bean
    RecoveryPort recoveryPort() {
        return new DryRunRecoveryPort();
    }

    /**
     * P1 tenant/quota/admin writes keep staging {@code platform.audit_event} and
     * also land on the existing hash chain. Same method as the caller used:
     * {@code append} joins the business TX; {@code appendNewTransaction} commits
     * privately (disable-reject / quota-reject).
     */
    @Bean
    @Primary
    @ConditionalOnBean(JdbcAuditPort.class)
    one.nexus.p1.port.AuditPort hashChainedP1AuditPort(JdbcAuditPort staging, AuditPort chain) {
        return new HashChainedP1AuditPort(staging, chain);
    }
}
