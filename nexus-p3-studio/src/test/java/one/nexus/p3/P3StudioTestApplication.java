package one.nexus.p3;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Test-only start class. Production start class is {@code one.nexus.bootstrap.NexusOneApplication}.
 *
 * <p>A bare {@code @SpringBootApplication} component-scans every {@code one.nexus.p3.**}
 * type on the test classpath, including sibling test hosts such as
 * {@code FlowableCompatibilityApplication} (which excludes {@code FlywayAutoConfiguration}).
 * Those excludes are merged into this context and drop the Flyway initializer that P3
 * JDBC repositories wait on. Exclude other Boot application/configuration hosts from
 * the scan so Flyway stays on for these ITs.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = "one.nexus.p3",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ANNOTATION,
                classes = {SpringBootApplication.class, SpringBootConfiguration.class}))
public class P3StudioTestApplication {}
