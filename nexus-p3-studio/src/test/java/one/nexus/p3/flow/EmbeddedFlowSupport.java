package one.nexus.p3.flow;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.springframework.test.context.DynamicPropertyRegistry;

public final class EmbeddedFlowSupport {
    private EmbeddedFlowSupport() {}

    public static EmbeddedPostgres start() throws Exception {
        EmbeddedPostgres postgres = EmbeddedPostgres.builder().start();
        assertPostgres15(postgres);
        try (Connection connection = postgres.getPostgresDatabase().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS flowable");
            statement.execute("CREATE SCHEMA IF NOT EXISTS business_meta");
            statement.execute("CREATE SCHEMA IF NOT EXISTS business_data");
            statement.execute("CREATE SCHEMA IF NOT EXISTS platform");
            statement.execute("CREATE SCHEMA IF NOT EXISTS iam");
            statement.execute("GRANT USAGE, CREATE ON SCHEMA business_data TO CURRENT_USER");
        }
        migrate(postgres);
        return postgres;
    }

    public static void migrate(EmbeddedPostgres postgres) {
        Flyway.configure()
                .dataSource(jdbcUrl(postgres), "postgres", "postgres")
                .locations("classpath:db/migration")
                .schemas("platform")
                .defaultSchema("platform")
                .createSchemas(true)
                .load()
                .migrate();
    }

    public static String jdbcUrl(EmbeddedPostgres postgres) {
        return "jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres";
    }

    public static void register(DynamicPropertyRegistry registry, EmbeddedPostgres postgres) {
        registry.add("spring.datasource.url", () -> jdbcUrl(postgres));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.schemas", () -> "platform");
        registry.add("spring.flyway.default-schema", () -> "platform");
        registry.add("spring.flyway.create-schemas", () -> "true");
        registry.add("nexus.flow.engine", () -> "flowable-7.1.0-in-process");
    }

    public static void assertPostgres15(EmbeddedPostgres postgres) throws Exception {
        try (Connection connection = postgres.getPostgresDatabase().getConnection();
             Statement statement = connection.createStatement();
             var rs = statement.executeQuery("SHOW server_version")) {
            rs.next();
            if (!rs.getString(1).startsWith("15.")) {
                throw new AssertionError("expected PostgreSQL 15, got " + rs.getString(1));
            }
        }
    }
}
