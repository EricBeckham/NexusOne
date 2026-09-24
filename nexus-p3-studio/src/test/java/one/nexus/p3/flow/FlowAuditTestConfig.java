package one.nexus.p3.flow;

import javax.sql.DataSource;
import one.nexus.audit.AuditPort;
import one.nexus.audit.JdbcHashChainedAuditPort;
import one.nexus.crypto.CryptoPort;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Lets the flow IT assert approve/reject audit rows. Product boot already publishes {@link AuditPort}. */
@TestConfiguration
class FlowAuditTestConfig {
    @Bean
    CryptoPort flowAuditCryptoPort() {
        return JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL);
    }

    @Bean
    AuditPort flowAuditPort(DataSource dataSource, CryptoPort flowAuditCryptoPort) {
        return new JdbcHashChainedAuditPort(dataSource, flowAuditCryptoPort);
    }
}
