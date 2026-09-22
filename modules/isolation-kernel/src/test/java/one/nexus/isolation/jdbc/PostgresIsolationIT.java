package one.nexus.isolation.jdbc;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;
import one.nexus.isolation.api.PlatformContext;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.QueryJoin;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.boundary.IsolatedExport;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.spi.FieldPolicy;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.query.NativeSqlPort;
import one.nexus.isolation.query.TableRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresIsolationIT {
    private static EmbeddedPostgres postgres;

    @BeforeAll
    static void startPostgres() throws Exception {
        postgres = EmbeddedPostgres.builder().start();
    }

    @AfterAll
    static void stopPostgres() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put((String) pairs[i], pairs[i + 1]);
        }
        return result;
    }

    @Test
    void dualTenantReadWriteAndRejectBeforeJdbc() throws Exception {
        try (Connection raw = postgres.getPostgresDatabase().getConnection()) {
            try (Statement version = raw.createStatement(); ResultSet rs = version.executeQuery("SHOW server_version")) {
                assertTrue(rs.next());
                assertTrue(rs.getString(1).startsWith("15."), "expected PostgreSQL 15, got " + rs.getString(1));
            }
            raw.setAutoCommit(false);
            try {
                try (Statement fixture = raw.createStatement()) {
                    fixture.execute("CREATE TABLE ledger_entry (id text PRIMARY KEY, tenant_id text NOT NULL, title text NOT NULL)");
                    fixture.execute("CREATE TABLE platform_metric (id text PRIMARY KEY, count integer NOT NULL)");
                    fixture.execute("""
                            CREATE TABLE table_registry (
                                table_name text PRIMARY KEY,
                                scope text NOT NULL CHECK (scope IN ('tenant', 'platform', 'app_shared')),
                                tenant_required boolean NOT NULL,
                                schema_version integer NOT NULL CHECK (schema_version > 0),
                                owner_module text NOT NULL
                            )
                            """);
                    fixture.execute("INSERT INTO table_registry VALUES ('ledger_entry', 'tenant', true, 1, 'p3')");
                    fixture.execute("INSERT INTO table_registry VALUES ('platform_metric', 'platform', false, 1, 'p1')");
                }
                CollectingAuditSink sink = new CollectingAuditSink();
                TableRegistry registry = new TableRegistry();
                registry.registerTenant("ledger_entry", "p3", 1, "id", "tenant_id", "title");
                registry.registerPlatform("platform_metric", "p1", 1, "id", "count");
                IsolationKernel kernel = IsolationKernel.create(registry, Set.of("ops.summary"), sink);
                TenantContext alpha = kernel.tenants().issue(new AuthenticatedMembership(
                        Actor.person("user-a"), "alpha", "mem-a", Set.of("alpha")), "trace-a");
                TenantContext beta = kernel.tenants().issue(new AuthenticatedMembership(
                        Actor.person("user-b"), "beta", "mem-b", Set.of("beta")), "trace-b");

                AtomicInteger executions = new AtomicInteger();
                Connection connection = counting(raw, executions);

                assertEquals(1, kernel.jdbc().executeUpdate(connection, QueryAst.insert("ledger_entry", map("id", "a1", "title", "alpha row")), alpha));
                assertEquals(1, kernel.jdbc().executeUpdate(connection, QueryAst.insert("ledger_entry", map("id", "b1", "title", "beta row")), beta));
                assertEquals(List.of("alpha row"), titles(kernel, connection, alpha));
                assertEquals(List.of("beta row"), titles(kernel, connection, beta));
                assertEquals(0, kernel.jdbc().executeUpdate(connection, QueryAst.update("ledger_entry", map("title", "tampered"), map("id", "b1")), alpha));
                assertEquals(0, kernel.jdbc().executeUpdate(connection, QueryAst.delete("ledger_entry", map("id", "b1")), alpha));
                assertEquals(List.of("beta row"), titles(kernel, connection, beta));

                int before = executions.get();
                Connection unusable = unusable();
                deny(IsolationErrorCode.CROSS_TENANT_WRITE, () ->
                        kernel.jdbc().executeUpdate(unusable, QueryAst.insert("ledger_entry", map("id", "x", "title", "bad", "tenant_id", "beta")), alpha));
                deny(IsolationErrorCode.CROSS_TENANT_WRITE, () ->
                        kernel.jdbc().executeUpdate(unusable, QueryAst.update("ledger_entry", map("title", "bad"), map("id", "b1", "tenant_id", "beta")), alpha));
                deny(IsolationErrorCode.MISSING_CONTEXT, () ->
                        kernel.jdbc().executeQuery(unusable, QueryAst.select("ledger_entry", List.of("title"), Map.of()), null));
                deny(IsolationErrorCode.ILLEGAL_PLATFORM_ACCESS, () ->
                        kernel.jdbc().executeQuery(unusable, QueryAst.select("platform_metric", List.of("id"), Map.of()), alpha));
                deny(IsolationErrorCode.ILLEGAL_PLATFORM_ACCESS, () ->
                        kernel.platform().issue("ops.other", Actor.platformAdmin("admin"), "review", "trace-1"));
                assertEquals(before, executions.get(), "denied requests must not reach JDBC");

                PlatformContext platform = kernel.platform().issue("ops.summary", Actor.platformAdmin("admin"), "review", "trace-1");
                assertEquals(2, kernel.jdbc().executeQuery(connection, QueryAst.select("ledger_entry", List.of("title"), Map.of()), platform).size());
                NativeSqlPort nativePort = kernel.nativeSql(Map.of("infra.count-ledger", "ledger_entry"));
                assertEquals(1, count(connection, nativePort.compileCount("infra.count-ledger", alpha)));
                assertEquals(1, count(connection, nativePort.compileCount("infra.count-ledger", beta)));
                assertEquals(2, count(connection, nativePort.compileCount("infra.count-ledger", platform)));
                deny(IsolationErrorCode.NATIVE_TEMPLATE_NOT_WHITELISTED, () -> nativePort.compileCount("business.raw-sql", alpha));
                assertTrue(sink.rejectCount() >= 5);
            } finally {
                raw.rollback();
            }
        }
    }

    @Test
    void joinAndFieldPolicyExportRejectedBeforeJdbc() throws Exception {
        try (Connection raw = postgres.getPostgresDatabase().getConnection()) {
            raw.setAutoCommit(false);
            try {
                try (Statement fixture = raw.createStatement()) {
                    fixture.execute("CREATE TABLE ledger_entry (id text PRIMARY KEY, tenant_id text NOT NULL, title text NOT NULL, hidden text)");
                    fixture.execute("CREATE TABLE ledger_comment (id text PRIMARY KEY, tenant_id text NOT NULL, entry_id text NOT NULL, body text NOT NULL)");
                }
                CollectingAuditSink sink = new CollectingAuditSink();
                TableRegistry registry = new TableRegistry();
                registry.registerTenant("ledger_entry", "p3", 1, "id", "tenant_id", "title", "hidden");
                registry.registerTenant("ledger_comment", "p3", 1, "id", "tenant_id", "entry_id", "body");
                IsolationKernel kernel = IsolationKernel.create(registry, Set.of("ops.summary"), sink);
                TenantContext alpha = kernel.tenants().issue(new AuthenticatedMembership(
                        Actor.person("user-a"), "alpha", "mem-a", Set.of("alpha")), "trace-join-a");
                TenantContext beta = kernel.tenants().issue(new AuthenticatedMembership(
                        Actor.person("user-b"), "beta", "mem-b", Set.of("beta")), "trace-join-b");

                AtomicInteger executions = new AtomicInteger();
                Connection connection = counting(raw, executions);
                kernel.jdbc().executeUpdate(connection, QueryAst.insert("ledger_entry",
                        map("id", "a1", "title", "alpha row", "hidden", "secret-a")), alpha);
                kernel.jdbc().executeUpdate(connection, QueryAst.insert("ledger_comment",
                        map("id", "c1", "entry_id", "a1", "body", "alpha comment")), alpha);
                kernel.jdbc().executeUpdate(connection, QueryAst.insert("ledger_entry",
                        map("id", "b1", "title", "beta row", "hidden", "secret-b")), beta);
                kernel.jdbc().executeUpdate(connection, QueryAst.insert("ledger_comment",
                        map("id", "c2", "entry_id", "b1", "body", "beta comment")), beta);

                List<Map<String, Object>> joined = kernel.jdbc().executeQuery(
                        connection,
                        QueryAst.select(
                                "ledger_entry",
                                List.of("title", "ledger_comment.body"),
                                Map.of(),
                                List.of(QueryJoin.inner("ledger_comment", "id", "entry_id"))),
                        alpha);
                assertEquals(1, joined.size());
                assertEquals("alpha row", joined.getFirst().get("title"));
                assertEquals("alpha comment", joined.getFirst().get("body"));

                int before = executions.get();
                Connection unusable = unusable();
                deny(IsolationErrorCode.CROSS_TENANT_READ, () ->
                        kernel.jdbc().executeQuery(
                                unusable,
                                QueryAst.select(
                                        "ledger_entry",
                                        List.of("id", "ledger_comment.body"),
                                        Map.of(),
                                        List.of(QueryJoin.inner("ledger_comment", "id", "entry_id", map("tenant_id", "beta")))),
                                alpha));

                AccessContextHolder.set(alpha);
                try {
                    deny(IsolationErrorCode.CROSS_TENANT_EXPORT, () ->
                            IsolatedExport.query(
                                    "beta",
                                    QueryAst.select("ledger_entry", List.of("title"), Map.of()),
                                    kernel.jdbc(),
                                    unusable));
                    deny(IsolationErrorCode.CROSS_TENANT_READ, () ->
                            IsolatedExport.query(
                                    "alpha",
                                    QueryAst.select(
                                            "ledger_entry",
                                            List.of("id", "ledger_comment.body"),
                                            Map.of(),
                                            List.of(QueryJoin.inner("ledger_comment", "id", "entry_id", map("tenant_id", "beta")))),
                                    kernel.jdbc(),
                                    unusable));
                    FieldPolicy policy = row -> {
                        Map<String, Object> out = new LinkedHashMap<>(row);
                        out.remove("hidden");
                        return out;
                    };
                    deny(IsolationErrorCode.CROSS_TENANT_EXPORT, () ->
                            IsolatedExport.queryAndProject(
                                    "beta",
                                    QueryAst.select("ledger_entry", List.of("title"), Map.of()),
                                    kernel.jdbc(),
                                    unusable,
                                    policy));
                    assertEquals(before, executions.get(), "denied join/export must not reach JDBC");
                    List<Map<String, Object>> projected = IsolatedExport.queryAndProject(
                            "alpha",
                            QueryAst.select("ledger_entry", List.of("title", "hidden"), Map.of()),
                            kernel.jdbc(),
                            connection,
                            policy);
                    assertEquals(1, projected.size());
                    assertEquals("alpha row", projected.getFirst().get("title"));
                    assertFalse(projected.getFirst().containsKey("hidden"));
                } finally {
                    AccessContextHolder.clear();
                }
                assertTrue(sink.rejected(IsolationErrorCode.CROSS_TENANT_READ));
                assertTrue(sink.rejected(IsolationErrorCode.CROSS_TENANT_EXPORT));
            } finally {
                raw.rollback();
            }
        }
    }

    private static List<String> titles(IsolationKernel kernel, Connection connection, TenantContext tenant) throws Exception {
        return kernel.jdbc().executeQuery(connection, QueryAst.select("ledger_entry", List.of("title"), Map.of()), tenant)
                .stream()
                .map(row -> String.valueOf(row.get("title")))
                .toList();
    }

    private static int count(Connection connection, one.nexus.isolation.api.RenderedSql sql) throws Exception {
        try (var statement = connection.prepareStatement(sql.sql())) {
            for (int i = 0; i < sql.parameters().size(); i++) {
                statement.setObject(i + 1, sql.parameters().get(i));
            }
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private static void deny(IsolationErrorCode code, Throwing action) {
        IsolationException ex = assertThrows(IsolationException.class, action::run);
        assertEquals(code, ex.error());
    }

    private static Connection counting(Connection real, AtomicInteger executions) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, args) -> {
            if (method.getName().startsWith("prepare") || method.getName().equals("createStatement") || method.getName().equals("nativeSQL")) {
                executions.incrementAndGet();
            }
            return method.invoke(real, args);
        });
    }

    private static Connection unusable() {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, args) -> {
            if ("toString".equals(method.getName())) {
                return "unusable-connection";
            }
            throw new AssertionError("JDBC " + method.getName() + " must not run for a rejected plan");
        });
    }

    @FunctionalInterface
    private interface Throwing {
        void run() throws Exception;
    }
}
