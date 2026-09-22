package one.nexus.p3.flow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.context.annotation.Import;

/** Test-only entrypoint. Product boot is {@code one.nexus.bootstrap.NexusOneApplication}. */
@SpringBootApplication(scanBasePackages = {"one.nexus.p3.flow", "one.nexus.p2", "one.nexus.permission"})
@Import(FlywayAutoConfiguration.class)
public class NexusP3FlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(NexusP3FlowApplication.class, args);
    }
}
