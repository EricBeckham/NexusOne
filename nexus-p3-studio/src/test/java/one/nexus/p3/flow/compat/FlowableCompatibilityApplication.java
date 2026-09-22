package one.nexus.p3.flow.compat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;

/** Isolated Boot 4.1.1 host for the Flowable 7.1.0 same-JVM spike. */
@SpringBootApplication(
        scanBasePackages = "one.nexus.p3.flow.engine",
        exclude = {FlywayAutoConfiguration.class}
)
public class FlowableCompatibilityApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlowableCompatibilityApplication.class, args);
    }
}
