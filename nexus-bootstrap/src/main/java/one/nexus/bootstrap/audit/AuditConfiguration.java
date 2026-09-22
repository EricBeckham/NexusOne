package one.nexus.bootstrap.audit;

import javax.sql.DataSource;
import one.nexus.audit.AuditPort;
import one.nexus.audit.InMemoryHashChainedAuditPort;
import one.nexus.audit.JdbcHashChainedAuditPort;
import one.nexus.audit.recovery.DryRunRecoveryPort;
import one.nexus.audit.recovery.RecoveryPort;
import one.nexus.crypto.CryptoPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
