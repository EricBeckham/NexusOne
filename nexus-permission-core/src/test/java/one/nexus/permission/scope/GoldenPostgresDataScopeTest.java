package one.nexus.permission.scope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import one.nexus.permission.PostgresAvailable;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.engine.PermissionEngine;
import one.nexus.permission.engine.PolicySnapshot;
import one.nexus.permission.engine.Predicate;
import one.nexus.permission.engine.Rule;
import one.nexus.permission.engine.Subject;
import org.junit.jupiter.api.Test;

/**
 * Product-path port of {@code poc/p0-02/golden_pg.py}: Alpha + Beta, cross-tenant
 * row excluded, tenant predicate cannot update the other tenant.
 */
class GoldenPostgresDataScopeTest {
    @Test
    void goldenScopeReturnsAlphaBetaAndRejectsCrossTenant() throws Exception {
        Subject subject = new Subject("t1", "u1", Set.of("employee"), Set.of("manager"), Set.of("d1"));
        PolicySnapshot snapshot = new PolicySnapshot("t1", 1, List.of(
                new Rule("role", "employee", "ledger", "read", Predicate.atom("owner_id", "=", "u1")),
                new Rule("position", "manager", "ledger", "read", Predicate.atom("department_id", "=", "d1"))));
        Decision decision = PermissionEngine.evaluate(subject, "t1", "ledger", "read", snapshot,
                List.of(Predicate.atom("id", "IN", List.of("r1"))),
                Predicate.atom("status", "=", "active"));
        assertTrue(decision.allowed());
        ScopedQuery scoped = PermissionQueryBinder.bind("t1", decision);
        BoundSql list = scoped.list("ledger_row", List.of("title"), "id", 50, 0);
        BoundSql count = scoped.count("ledger_row");
        BoundSql detailDelta = scoped.detail("ledger_row", List.of("title"), "id", "r4");
        BoundSql detailOther = scoped.detail("ledger_row", List.of("title"), "id", "r9");

        try (Connection connection = PostgresAvailable.openOrAbort()) {
            connection.setAutoCommit(false);
            try (var stmt = connection.createStatement()) {
                stmt.execute("""
                        CREATE TEMP TABLE ledger_row (
                          id text PRIMARY KEY,
                          tenant_id text NOT NULL,
                          owner_id text NOT NULL,
                          department_id text NOT NULL,
                          status text NOT NULL,
                          title text NOT NULL
                        )
                        """);
                stmt.execute("""
                        INSERT INTO ledger_row VALUES
                          ('r1','t1','u1','d1','active','Alpha'),
                          ('r2','t1','u2','d1','active','Beta'),
                          ('r3','t1','u9','d2','inactive','Gamma'),
                          ('r4','t1','u9','d2','active','Delta'),
                          ('r9','t2','u1','d1','active','Other')
                        """);
            }
            List<String> titles = queryTitles(connection, list);
            assertEquals(List.of("Alpha", "Beta"), titles);
            try (PreparedStatement countStmt = connection.prepareStatement(count.sql())) {
                bind(countStmt, count.parameters());
                try (ResultSet rs = countStmt.executeQuery()) {
                    rs.next();
                    assertEquals(2L, rs.getLong(1));
                }
            }
            assertTrue(queryTitles(connection, detailDelta).isEmpty());
            assertTrue(queryTitles(connection, detailOther).isEmpty());

            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE ledger_row SET title = 'tampered' WHERE tenant_id = ? AND id = ?")) {
                update.setString(1, "t1");
                update.setString(2, "r9");
                assertEquals(0, update.executeUpdate());
            }
            try (var rs = connection.createStatement().executeQuery("SELECT title FROM ledger_row WHERE id = 'r9'")) {
                rs.next();
                assertEquals("Other", rs.getString(1));
            }
            connection.rollback();
        }
    }

    private static List<String> queryTitles(Connection connection, BoundSql query) throws Exception {
        List<String> titles = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(query.sql())) {
            bind(stmt, query.parameters());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    titles.add(rs.getString(1));
                }
            }
        }
        return titles;
    }

    private static void bind(PreparedStatement stmt, List<Object> params) throws Exception {
        for (int i = 0; i < params.size(); i++) {
            Object value = params.get(i);
            if (value instanceof Integer number) {
                stmt.setInt(i + 1, number);
            } else if (value instanceof Long number) {
                stmt.setLong(i + 1, number);
            } else {
                stmt.setString(i + 1, String.valueOf(value));
            }
        }
    }
}
