package one.nexus.permission;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

/** Test-only entrypoint. Product boot is {@code one.nexus.bootstrap.NexusOneApplication}. */
@SpringBootApplication(
        scanBasePackages = {"one.nexus.permission", "one.nexus.p2"},
        exclude = {DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class}
)
public class NexusPermissionApplication {
    public static void main(String[] args) {
        SpringApplication.run(NexusPermissionApplication.class, args);
    }
}
