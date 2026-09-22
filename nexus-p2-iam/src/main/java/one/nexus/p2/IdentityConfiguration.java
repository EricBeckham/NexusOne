package one.nexus.p2;

import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.idp.IdentityProviderPort;
import one.nexus.p2.idp.KeycloakIdentityProvider;
import one.nexus.p2.idp.StubIdentityProvider;
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.p2.persist.JdbcIdentityRepository;
import one.nexus.p2.smtp.KeycloakSmtpHook;
import one.nexus.p2.smtp.SmtpHook;
import org.flywaydb.core.Flyway;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(IdentityConfiguration.IamProperties.class)
public class IdentityConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    IdentityProviderPort identityProviderPort(IamProperties properties) {
        if ("keycloak".equalsIgnoreCase(properties.idp())) {
            return new KeycloakIdentityProvider(
                    properties.keycloak().issuer(),
                    properties.keycloak().clientId(),
                    properties.keycloak().clientSecret());
        }
        return new StubIdentityProvider();
    }

    @Bean
    IdentityRepository identityRepository(IamProperties properties) {
        if ("jdbc".equalsIgnoreCase(properties.persistence())) {
            DataSource dataSource = DataSourceBuilder.create()
                    .url(properties.jdbc().url())
                    .username(properties.jdbc().username())
                    .password(properties.jdbc().password())
                    .driverClassName("org.postgresql.Driver")
                    .build();
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .schemas("iam")
                    .defaultSchema("iam")
                    .createSchemas(true)
                    .load()
                    .migrate();
            return new JdbcIdentityRepository(new JdbcTemplate(dataSource));
        }
        return new InMemoryIdentityRepository();
    }

    @Bean
    SmtpHook smtpHook(IdentityProviderPort idp, IamProperties properties) {
        return new KeycloakSmtpHook(idp, properties.smtp().enabled());
    }

    @Bean
    IdentityModule identityModule(
            IdentityRepository repository,
            IdentityProviderPort idp,
            SmtpHook smtp,
            Clock clock,
            IamProperties properties
    ) {
        return new IdentityModule(repository, idp, smtp, clock, Duration.parse(properties.contextTtl()));
    }

    @ConfigurationProperties(prefix = "nexus.iam")
    public record IamProperties(
            String persistence,
            String idp,
            String contextTtl,
            Jdbc jdbc,
            Keycloak keycloak,
            Smtp smtp,
            String platformBootstrapToken
    ) {
        public IamProperties {
            if (persistence == null) persistence = "memory";
            if (idp == null) idp = "stub";
            if (contextTtl == null) contextTtl = "PT8H";
            if (jdbc == null) jdbc = new Jdbc("jdbc:postgresql://127.0.0.1:5432/nexusone", "postgres", "");
            if (keycloak == null) {
                keycloak = new Keycloak(
                        "http://127.0.0.1:8080/realms/nexus",
                        "nexus-platform",
                        "",
                        "http://127.0.0.1:8088/api/v1/auth/callback");
            }
            if (smtp == null) smtp = new Smtp(false);
            if (platformBootstrapToken == null) platformBootstrapToken = "";
        }

        public record Jdbc(String url, String username, String password) {}

        public record Keycloak(String issuer, String clientId, String clientSecret, String redirectUri) {}

        public record Smtp(boolean enabled) {}
    }
}
