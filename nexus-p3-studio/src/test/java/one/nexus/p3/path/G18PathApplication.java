package one.nexus.p3.path;

import javax.sql.DataSource;
import one.nexus.audit.AuditPort;
import one.nexus.audit.JdbcHashChainedAuditPort;
import one.nexus.crypto.CryptoPort;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import one.nexus.p3.P3StudioTestApplication;
import one.nexus.p3.flow.NexusP3FlowApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

/** Test-only G18 path. Product boot is {@code one.nexus.bootstrap.NexusOneApplication}. */
@SpringBootApplication(
        scanBasePackages = {
            "one.nexus.p3",
            "one.nexus.p2",
            "one.nexus.permission",
            "one.nexus.event",
            "one.nexus.notification"
        },
        excludeFilters = {
            @ComponentScan.Filter(type = FilterType.REGEX, pattern = "one\\.nexus\\.p3\\.flow\\.compat\\..*"),
            @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = {P3StudioTestApplication.class, NexusP3FlowApplication.class})
        })
@Import(FlywayAutoConfiguration.class)
public class G18PathApplication {
    public static void main(String[] args) {
        SpringApplication.run(G18PathApplication.class, args);
    }

    @Bean
    CryptoPort cryptoPort() {
        return JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL);
    }

    @Bean
    AuditPort auditPort(DataSource dataSource, CryptoPort cryptoPort) {
        return new JdbcHashChainedAuditPort(dataSource, cryptoPort);
    }
}
