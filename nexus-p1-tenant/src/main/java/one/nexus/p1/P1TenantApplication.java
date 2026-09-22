package one.nexus.p1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import one.nexus.p1.config.P1Properties;

@SpringBootApplication(exclude = FlywayAutoConfiguration.class)
@EnableConfigurationProperties(P1Properties.class)
public class P1TenantApplication {

    public static void main(String[] args) {
        SpringApplication.run(P1TenantApplication.class, args);
    }
}
