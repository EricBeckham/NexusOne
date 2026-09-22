package one.nexus.task;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import one.nexus.event.isolation.ExecutionIsolation;
import one.nexus.event.isolation.StoredMembership;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.context.AccessContextHolder;
import org.flywaydb.core.Flyway;
import org.postgresql.ds.PGSimpleDataSource;

final class EmbeddedTaskSupport {
    private EmbeddedTaskSupport() {}

    static EmbeddedPostgres start() throws Exception {
        return EmbeddedPostgres.builder().start();
    }

    static String jdbcUrl(EmbeddedPostgres postgres) {
        return "jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres";
    }

    static DataSource dataSource(EmbeddedPostgres postgres) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(jdbcUrl(postgres));
        dataSource.setUser("postgres");
        dataSource.setPassword("postgres");
        return dataSource;
    }

    static void migrate(EmbeddedPostgres postgres) {
        Flyway.configure()
                .dataSource(jdbcUrl(postgres), "postgres", "postgres")
                .locations("classpath:db/migration")
                .schemas("platform")
                .defaultSchema("platform")
                .load()
                .migrate();
    }

    static void bind(ExecutionIsolation isolation, String tenantId, String traceId) {
        TenantContext context = isolation.restoreTenant(
                new StoredMembership(tenantId, "user-" + tenantId, Actor.PERSON, "mem-" + tenantId),
                traceId);
        AccessContextHolder.set(context);
    }

    static void assertPostgres15(EmbeddedPostgres postgres) throws Exception {
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
