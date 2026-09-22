package one.nexus.p1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import one.nexus.p1.config.P1Properties;

/**
 * Temporary runnable for M0-03 demos. Remove when this module is a library under
 * {@code nexus-bootstrap} ({@code @SpringBootApplication(scanBasePackages = "one.nexus")}).
 */
@SpringBootApplication
@EnableConfigurationProperties(P1Properties.class)
public class P1TenantApplication {

    public static void main(String[] args) {
        SpringApplication.run(P1TenantApplication.class, args);
    }
}
