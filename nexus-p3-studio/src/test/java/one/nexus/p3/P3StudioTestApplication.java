package one.nexus.p3;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Test-only start class. Production start class is {@code one.nexus.bootstrap.NexusOneApplication}.
 *
 * <p>A bare {@code @SpringBootApplication} component-scans every {@code one.nexus.p3.**}
 * type on the test classpath, including sibling test hosts such as
 * {@code FlowableCompatibilityApplication} (which excludes {@code FlywayAutoConfiguration})
 * and {@code @TestConfiguration} classes such as the Flowable spike datasource. Those
 * excludes and extra beans are merged into this context. Keep Boot's default type
 * exclude filters and drop other Boot application / test-configuration hosts so Flyway
 * stays on and M0-08 spike beans stay out.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = "one.nexus.p3",
        excludeFilters = {
            @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
            @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
            @ComponentScan.Filter(
                    type = FilterType.ANNOTATION,
                    classes = {
                        SpringBootApplication.class,
                        SpringBootConfiguration.class,
                        TestConfiguration.class
                    })
        })
public class P3StudioTestApplication {}
