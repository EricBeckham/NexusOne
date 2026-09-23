package one.nexus.permission.scope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.QueryPage;
import one.nexus.isolation.api.QuerySort;
import one.nexus.isolation.api.TableDescriptor;
import one.nexus.isolation.api.TableScope;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.engine.Predicate;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * G1 leftover beyond #146: leftover BoundSql / COUNT stay on
 * {@link QueryAstScopedQuery} → GuardedJdbc. Live product path is honest
 * SKIP when unavailable. Does not redo #142 / #146 / #162 / #168. Does not
 * claim product G1 close. G18 stays closed.
 */
class G1QueryAstBoundSqlHonestyTest {
    private final TableDescriptor table = new TableDescriptor(
            "ledger_entry",
            TableScope.TENANT,
            Set.of("id", "tenant_id", "title", "owner_id", "department_id", "deleted_at", "phone"),
            "p3",
            1);

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void leftoverBoundSqlAndCountStayGuarded() throws IOException {
        IsolationKernel kernel = kernel();
        AccessContextHolder.set(kernel.tenants().issue(
                new AuthenticatedMembership(Actor.person("u1"), "alpha", "mem-a", Set.of("alpha")),
                "trace-boundsql-honesty"));
        Predicate leftoverScope = Predicate.anyOf(
                Predicate.atom("title", "=", "x"),
                Predicate.atom("phone", "=", "1"));
        QueryAstScopedQuery.CompiledRead leftover = QueryAstScopedQuery.compile(
                QueryAst.select("ledger_entry", List.of("id"), filters("deleted_at", null))
                        .withSorts(List.of(QuerySort.asc("id")))
                        .withPage(QueryPage.of(10, 0)),
                new Decision(true, "leftover", leftoverScope, Map.of(), 1L),
                table,
                kernel.compiler(),
                AccessContextHolder.current());
        assertFalse(leftover.scopeFullyEncoded());
        assertTrue(leftover.executed().sql().contains(QueryAstScopedQuery.COMPILER_TENANT));
        assertTrue(leftover.executed().sql().contains("title = ?"));
        assertTrue(leftover.executed().sql().indexOf("AND (")
                < leftover.executed().sql().toLowerCase().indexOf(" order by "));
        assertEquals(leftover.compiled().plan(), leftover.executed().plan());

        var count = QueryAstScopedQuery.countExecuted(leftover);
        assertTrue(count.sql().startsWith("SELECT count(*) FROM ("));
        assertTrue(count.sql().contains(QueryAstScopedQuery.COMPILER_TENANT));
        assertTrue(count.sql().contains("title = ?"));
        assertEquals(leftover.compiled().plan(), count.plan());

        assertThrows(PermissionException.class, () -> QueryAstScopedQuery.count(
                new BoundSql("SELECT id FROM ledger_entry WHERE tenant_id = ?", List.of("alpha"))));
        assertThrows(PermissionException.class, () -> QueryAstScopedQuery.count(
                new BoundSql("SELECT count(*) FROM ledger_entry", List.of())));

        String store = readExisting(G1QueryAstBoundSqlHonesty.guardedLedgerStorePath());
        assertTrue(G1QueryAstBoundSqlHonesty.leftoverBoundSqlGuarded(store));
    }

    @Test
    void liveInventoryIsSkipWithoutObservedWalk() {
        G1QueryAstBoundSqlHonesty.Inventory inventory = G1QueryAstBoundSqlHonesty.probeLive();
        assertEquals("SKIP", inventory.liveProductWalk());
        assertEquals("SKIP", inventory.uiH5Report());
        assertFalse(inventory.skipReason().isBlank());
        if (!inventory.livePresent()) {
            assertFalse(inventory.endpointSet() && inventory.issuedContextSet() && inventory.listenerPresent());
        }

        Map<String, String> report = G1QueryAstBoundSqlHonesty.honestSkipTemplate(inventory);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        G1QueryAstBoundSqlHonesty.verify(report);
        assertEquals("NOT_CLAIMED", report.get("g1_close"));
        assertEquals("PASS", report.get("leftover_boundsql_guard"));
        assertEquals("PASS", report.get("leftover_count_guard"));
        assertEquals("true", report.get("raw_boundsql_rejected"));
        assertEquals("false", report.get("count_is_ast_node"));
        assertEquals("QueryAstScopedQuery", report.get("leftover_surface"));
        assertEquals("true", report.get("leftover_rows_kept"));
        assertTrue(report.get("leftover_rows").contains("g1-queryast-boundsql-guard"));
        assertTrue(report.get("leftover_rows").contains("g1-datascope-or-in-ast"));
        assertTrue(report.get("leftover_rows").contains("g1-fieldpolicy-mask-second-auth"));
        assertTrue(report.get("leftover_rows").contains("g1-fieldpolicy-mask-second-auth-honesty"));
        assertEquals("false", report.get("redone_142"));
        assertEquals("false", report.get("redone_146"));
        assertEquals("false", report.get("redone_162"));
        assertEquals("false", report.get("redone_168"));
        assertEquals("closed_unchanged", report.get("g18_status"));
        assertEquals("false", report.get("p0_m0_redone"));
        assertEquals("NOT_CLAIMED", G1QueryAstBoundSqlHonesty.G1_CLOSE);
    }

    @Test
    void honestyGateAcceptsHonestSkipFixture() {
        G1QueryAstBoundSqlHonesty.verify(G1QueryAstBoundSqlHonesty.parseReport(fixture("honest-skip.env")));
    }

    @Test
    void honestyGateRejectsFakeLiveProductPass() {
        Map<String, String> fake = new HashMap<>(
                G1QueryAstBoundSqlHonesty.parseReport(fixture("fake-live-product-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G1QueryAstBoundSqlHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("live_product_walk"));
    }

    @Test
    void honestyGateRejectsG1CloseAndRawBoundSql() {
        IllegalArgumentException close = assertThrows(IllegalArgumentException.class,
                () -> G1QueryAstBoundSqlHonesty.verify(
                        G1QueryAstBoundSqlHonesty.parseReport(fixture("fake-g1-close.env"))));
        assertTrue(close.getMessage().contains("g1_close"));

        IllegalArgumentException raw = assertThrows(IllegalArgumentException.class,
                () -> G1QueryAstBoundSqlHonesty.verify(
                        G1QueryAstBoundSqlHonesty.parseReport(fixture("fake-raw-boundsql.env"))));
        assertTrue(raw.getMessage().contains("raw_boundsql_rejected")
                || raw.getMessage().contains("leftover_boundsql_guard"));
    }

    @Test
    void honestyGateRejectsSiblingRedosAndDroppedLeftoverRows() {
        Map<String, String> fake = new HashMap<>(
                G1QueryAstBoundSqlHonesty.parseReport(fixture("honest-skip.env")));
        fake.put("redone_146", "true");
        IllegalArgumentException redo = assertThrows(IllegalArgumentException.class,
                () -> G1QueryAstBoundSqlHonesty.verify(fake));
        assertTrue(redo.getMessage().contains("redone_146"));

        fake.put("redone_146", "false");
        fake.put("leftover_rows", "g1-queryast-boundsql-guard");
        IllegalArgumentException rows = assertThrows(IllegalArgumentException.class,
                () -> G1QueryAstBoundSqlHonesty.verify(fake));
        assertTrue(rows.getMessage().contains("leftover_rows"));
    }

    @Test
    void docsReadmeKeepsAllSiblingLeftoverRows() throws IOException {
        String text = readExisting(repoFile("docs/README.md"));
        assertTrue(text.contains("[g1-queryast-boundsql-guard](./g1-queryast-boundsql-guard.md)"));
        assertTrue(text.contains("[g1-queryast-boundsql-honesty](./g1-queryast-boundsql-honesty.md)"));
        assertTrue(text.contains("[g1-datascope-or-in-ast](./g1-datascope-or-in-ast.md)"));
        assertTrue(text.contains("[g1-fieldpolicy-mask-second-auth](./g1-fieldpolicy-mask-second-auth.md)"));
        assertTrue(text.contains("[g1-fieldpolicy-mask-second-auth-honesty](./g1-fieldpolicy-mask-second-auth-honesty.md)"));
        assertTrue(text.contains("[g1-queryast-product-optin](./g1-queryast-product-optin.md)"));
        assertTrue(text.contains("[g1-export-fieldpolicy-drop](./g1-export-fieldpolicy-drop.md)"));
        assertTrue(text.contains("[g4-hot-purge-90d-honesty](./g4-hot-purge-90d-honesty.md)"));
        assertTrue(text.contains("[g17-separation-honesty](./g17-separation-honesty.md)"));
        assertTrue(text.contains("[g10-identity-drift-honesty](./g10-identity-drift-honesty.md)"));
        assertTrue(text.contains("[g15-native-bridge-honesty](./g15-native-bridge-honesty.md)"));
        assertTrue(text.contains("[g4-pkce-spa-verifier-honesty](./g4-pkce-spa-verifier-honesty.md)"));
        assertTrue(text.contains("[s0-secret-getenv-snapshot-honesty](./s0-secret-getenv-snapshot-honesty.md)"));
        assertTrue(text.contains("[g5-browser-failed-publish](./g5-browser-failed-publish.md)"));
        assertTrue(text.contains("[s0s1-live-redis-minio-isolation-honesty](./s0s1-live-redis-minio-isolation-honesty.md)"));
        assertTrue(text.contains("[g6-recovery-port-dry-run-honesty](./g6-recovery-port-dry-run-honesty.md)"));
        assertTrue(text.contains("[g4-p1-staging-audit-events-honesty](./g4-p1-staging-audit-events-honesty.md)"));
        assertTrue(text.contains("[linux-arm-profile-leftover-honesty](./linux-arm-profile-leftover-honesty.md)"));
        assertTrue(text.contains("[g7-query-expense-travel-honesty](./g7-query-expense-travel-honesty.md)"));
        assertTrue(text.contains("[s0-secret-rotate-without-restart-honesty](./s0-secret-rotate-without-restart-honesty.md)"));
        assertTrue(text.contains("[g15-wx-dt-native-bridge-honesty](./g15-wx-dt-native-bridge-honesty.md)"));
        assertFalse(text.toLowerCase().contains("product g1 closed"));
        assertFalse(text.contains("产品 G1 已关闭"));

        String claims = readExisting(repoFile("docs/planning/团队领取任务清单_V1.1.md"));
        assertTrue(claims.contains("g1-queryast-boundsql-guard.md"));
        assertTrue(claims.contains("g1-queryast-boundsql-honesty.md"));
        assertTrue(claims.contains("g1-fieldpolicy-mask-second-auth-honesty.md"));
        assertTrue(claims.contains("不宣称产品 G1 关闭"));
        assertTrue(claims.contains("不要重做") && claims.contains("#146"));
        assertFalse(claims.contains("产品 G1 已关闭"));
    }

    private static Map<String, Object> filters(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put((String) pairs[i], pairs[i + 1]);
        }
        return result;
    }

    private static IsolationKernel kernel() {
        TableRegistry registry = new TableRegistry();
        registry.registerTenant(
                "ledger_entry",
                "p3",
                1,
                "id",
                "tenant_id",
                "title",
                "owner_id",
                "department_id",
                "deleted_at",
                "phone");
        return IsolationKernel.create(registry, Set.of("ops.summary"), new CollectingAuditSink());
    }

    private static Path fixture(String name) {
        Path module = Path.of("scripts/g1-queryast-boundsql-honesty/fixtures", name);
        if (module.toFile().isFile()) {
            return module;
        }
        return Path.of("../scripts/g1-queryast-boundsql-honesty/fixtures", name);
    }

    private static Path repoFile(String relative) {
        Path here = Path.of(relative);
        if (here.toFile().isFile()) {
            return here;
        }
        return Path.of("..", relative);
    }

    private static String readExisting(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), path.toAbsolutePath().toString());
        return Files.readString(path);
    }
}
