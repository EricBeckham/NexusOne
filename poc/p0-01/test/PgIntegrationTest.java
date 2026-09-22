import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Real PostgreSQL contract; direct DDL is test fixture setup only. */
public final class PgIntegrationTest {
    private static int assertions;
    private static int databaseExecutions;
    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
    private static void check(boolean value) {
        assertions++;
        if (!value) throw new AssertionError("PostgreSQL assertion " + assertions + " failed");
    }
    private static int write(Connection connection, Poc.RenderedSql sql) throws Exception {
        databaseExecutions++;
        try (PreparedStatement statement = connection.prepareStatement(sql.sql)) {
            bind(statement, sql);
            return statement.executeUpdate();
        }
    }
    private static List<String> read(Connection connection, Poc.RenderedSql sql) throws Exception {
        databaseExecutions++;
        try (PreparedStatement statement = connection.prepareStatement(sql.sql)) {
            bind(statement, sql);
            try (ResultSet results = statement.executeQuery()) {
                List<String> titles = new ArrayList<String>();
                while (results.next()) titles.add(results.getString(1));
                return titles;
            }
        }
    }
    private static void bind(PreparedStatement statement, Poc.RenderedSql sql) throws Exception {
        for (int i = 0; i < sql.parameters.size(); i++) statement.setObject(i + 1, sql.parameters.get(i));
    }
    private static void deniedBeforeExecute(Runnable compileOnly) {
        int before = databaseExecutions;
        assertions++;
        try { compileOnly.run(); } catch (SecurityException expected) {
            if (before != databaseExecutions) throw new AssertionError("denied request reached JDBC");
            return;
        }
        throw new AssertionError("expected pre-JDBC rejection");
    }
    public static void main(String[] args) throws Exception {
        String host = env("PGHOST", "127.0.0.1");
        String port = env("PGPORT", "5432");
        String url = "jdbc:postgresql://" + host + ":" + port + "/" + System.getenv("PGDATABASE");
        String password = System.getenv("PGPASSWORD");
        try (Connection connection = DriverManager.getConnection(url, System.getenv("PGUSER"), password == null ? "" : password)) {
            connection.setAutoCommit(false);
            try {
                try (Statement fixture = connection.createStatement()) {
                    fixture.execute("CREATE SCHEMA IF NOT EXISTS p001");
                    fixture.execute("SET LOCAL search_path TO p001");
                    fixture.execute("CREATE TABLE ledger_entry (id text PRIMARY KEY, tenant_id text NOT NULL, title text NOT NULL)");
                    fixture.execute("CREATE TABLE platform_metric (id text PRIMARY KEY, count integer NOT NULL)");
                }
                Poc.TableRegistry registry = new Poc.TableRegistry();
                registry.register("ledger_entry", Poc.Scope.TENANT, "id", "tenant_id", "title");
                registry.register("platform_metric", Poc.Scope.PLATFORM, "id", "count");
                Poc.Executor executor = new Poc.Executor(registry, Collections.singleton("ops.summary"));
                Poc.TenantContext alpha = new Poc.TenantContext("alpha");
                Poc.TenantContext beta = new Poc.TenantContext("beta");
                check(write(connection, executor.compile(Poc.Query.insert("ledger_entry", map("id", "a1", "title", "alpha row")), alpha)) == 1);
                check(write(connection, executor.compile(Poc.Query.insert("ledger_entry", map("id", "b1", "title", "beta row")), beta)) == 1);
                check(read(connection, executor.compile(Poc.Query.select("ledger_entry", Arrays.asList("title"), map()), alpha)).equals(Arrays.asList("alpha row")));
                check(read(connection, executor.compile(Poc.Query.select("ledger_entry", Arrays.asList("title"), map()), beta)).equals(Arrays.asList("beta row")));
                check(write(connection, executor.compile(Poc.Query.update("ledger_entry", map("title", "tampered"), map("id", "b1")), alpha)) == 0);
                check(write(connection, executor.compile(Poc.Query.delete("ledger_entry", map("id", "b1")), alpha)) == 0);
                check(read(connection, executor.compile(Poc.Query.select("ledger_entry", Arrays.asList("title"), map()), beta)).equals(Arrays.asList("beta row")));

                deniedBeforeExecute(() -> executor.compile(Poc.Query.insert("ledger_entry", map("id", "x", "title", "bad", "tenant_id", "beta")), alpha));
                deniedBeforeExecute(() -> executor.compile(Poc.Query.update("ledger_entry", map("title", "bad"), map("id", "b1", "tenant_id", "beta")), alpha));
                deniedBeforeExecute(() -> executor.compile(Poc.Query.delete("ledger_entry", map("id", "b1", "tenant_id", "beta")), alpha));
                deniedBeforeExecute(() -> executor.compile(Poc.Query.select("ledger_entry", Arrays.asList("title"), map()), null));
                deniedBeforeExecute(() -> executor.compile(Poc.Query.select("platform_metric", Arrays.asList("id"), map()), alpha));
                List<String> audit = new ArrayList<String>();
                Poc.PlatformContextIssuer issuer = new Poc.PlatformContextIssuer(Collections.singleton("ops.summary"), audit::add);
                deniedBeforeExecute(() -> issuer.issue("ops.other", "admin", "review", "trace-1"));
                Poc.PlatformContext platform = issuer.issue("ops.summary", "admin", "review", "trace-1");
                check(audit.size() == 1);
                check(read(connection, executor.compile(Poc.Query.select("ledger_entry", Arrays.asList("title"), map()), platform)).size() == 2);
                Poc.NativeSqlPort nativePort = new Poc.NativeSqlPort(executor, Collections.singletonMap("infra.count-ledger", "ledger_entry"));
                check(count(connection, nativePort.compileCount("infra.count-ledger", alpha)) == 1);
                check(count(connection, nativePort.compileCount("infra.count-ledger", beta)) == 1);
                check(count(connection, nativePort.compileCount("infra.count-ledger", platform)) == 2);
                deniedBeforeExecute(() -> nativePort.compileCount("business.raw-sql", alpha));
                deniedBeforeExecute(() -> nativePort.compileCount("infra.count-ledger", null));
                System.out.println("PASS " + assertions + " PostgreSQL integration assertions, " + databaseExecutions + " guarded JDBC executions");
            } finally {
                connection.rollback(); // Fixture schema/tables/data disappear after test.
            }
        }
    }
    private static String env(String name, String fallback) {
        String value = System.getenv(name); return value == null || value.isEmpty() ? fallback : value;
    }
    private static int count(Connection connection, Poc.RenderedSql sql) throws Exception {
        databaseExecutions++;
        try (PreparedStatement statement = connection.prepareStatement(sql.sql)) {
            bind(statement, sql);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new AssertionError("missing count row");
                return result.getInt(1);
            }
        }
    }
}
