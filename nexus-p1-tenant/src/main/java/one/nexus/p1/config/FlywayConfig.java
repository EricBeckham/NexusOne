package one.nexus.p1.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 3.5 Flyway auto-config still calls APIs removed in Flyway 13.
 * P0 locked Flyway 13.7.0, so this module migrates explicitly.
 */
@Configuration
public class FlywayConfig {

    @Bean(initMethod = "migrate")
    public Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("p1")
                .defaultSchema("p1")
                .table("p1_flyway_history")
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .load();
    }
}
