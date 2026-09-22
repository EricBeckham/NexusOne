package one.nexus.p3.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * G7 leftover honesty: expense/travel list/detail/todo query surfaces stay
 * PASS; live JAR/IdP HTTP query is honest SKIP when unavailable. Does not
 * redo #103 / #113 / #140 / #159 / #175. Does not claim product G7 close.
 */
class G7QueryExpenseTravelHonestyTest {

    @Test
    void querySurfacesStayPassWithoutRerunning140() {
        G7QueryExpenseTravelHonesty.Surfaces surfaces = G7QueryExpenseTravelHonesty.catalog();
        assertEquals("PASS", surfaces.querySurface());
        assertTrue(surfaces.expense());
        assertTrue(surfaces.travel());
        assertTrue(surfaces.querySurfacesWork());
        assertTrue(surfaces.present().contains("GET /api/v1/p3/ledgers/{id}/records"));
        assertTrue(surfaces.present().contains("GET /api/v1/todos/pending"));
        assertTrue(surfaces.present().contains("GET /api/v1/p3/todos"));
    }

    @Test
    void liveInventoryIsSkipWithoutObservedWalk() {
        G7QueryExpenseTravelHonesty.Inventory inventory = G7QueryExpenseTravelHonesty.probeLive();
        assertEquals("SKIP", inventory.liveJarIdpQuery());
        assertFalse(inventory.skipReason().isBlank());
        if (!inventory.livePresent()) {
            assertFalse(inventory.queryObserved());
        }

        Map<String, String> report = G7QueryExpenseTravelHonesty.honestSkipTemplate(inventory);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        G7QueryExpenseTravelHonesty.verify(report);
        assertEquals("NOT_CLAIMED", report.get("g7_close"));
        assertEquals("PASS", report.get("query_surface"));
        assertEquals("PASS", report.get("expense_query_surface"));
        assertEquals("PASS", report.get("travel_query_surface"));
        assertEquals("SKIP", report.get("live_jar_idp_query"));
        assertEquals("false", report.get("query_walk_observed"));
        assertEquals("false", report.get("g7_140_redone"));
        assertEquals("false", report.get("g7_175_redone"));
        assertEquals("closed_unchanged", report.get("g18_status"));
    }

    @Test
    void honestyGateAcceptsHonestSkipFixture() {
        G7QueryExpenseTravelHonesty.verify(
                G7QueryExpenseTravelHonesty.parseReport(fixtureFile("honest-skip.env")));
    }

    @Test
    void honestyGateRejectsFakeLivePass() {
        Map<String, String> fake = new HashMap<>(
                G7QueryExpenseTravelHonesty.parseReport(fixtureFile("fake-live-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G7QueryExpenseTravelHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("live_jar_idp_query"));
    }

    @Test
    void honestyGateRejectsG7CloseClaim() {
        Map<String, String> fake = new HashMap<>(
                G7QueryExpenseTravelHonesty.parseReport(fixtureFile("fake-g7-close.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G7QueryExpenseTravelHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("g7_close"));
    }

    @Test
    void honestyGateRejectsWithdrawAnd140Redos() {
        Map<String, String> fake = new HashMap<>(
                G7QueryExpenseTravelHonesty.parseReport(fixtureFile("honest-skip.env")));
        fake.put("g7_140_redone", "true");
        IllegalArgumentException redo140 = assertThrows(IllegalArgumentException.class,
                () -> G7QueryExpenseTravelHonesty.verify(fake));
        assertTrue(redo140.getMessage().contains("g7_140_redone"));

        fake.put("g7_140_redone", "false");
        fake.put("g7_175_redone", "true");
        IllegalArgumentException redo175 = assertThrows(IllegalArgumentException.class,
                () -> G7QueryExpenseTravelHonesty.verify(fake));
        assertTrue(redo175.getMessage().contains("g7_175_redone"));

        fake.put("g7_175_redone", "false");
        fake.put("withdraw_paths_redone", "true");
        IllegalArgumentException withdraw = assertThrows(IllegalArgumentException.class,
                () -> G7QueryExpenseTravelHonesty.verify(fake));
        assertTrue(withdraw.getMessage().contains("withdraw_paths_redone"));
    }

    @Test
    void leftoverDocsStayHonestAndKeepSiblingRows() throws IOException {
        String leftover = readExisting(repoFile("docs/g7-query-expense-travel-honesty.md"));
        String index = readExisting(repoFile("docs/README.md"));
        String claim = readExisting(repoFile("docs/planning/团队领取任务清单_V1.1.md"));
        String query = readExisting(repoFile("docs/g7-query-expense-travel.md"));
        String it = readExisting(repoFile(
                "nexus-p3-studio/src/test/java/one/nexus/p3/path/G7ExpenseTravelQueryIT.java"));
        String lower = leftover.toLowerCase(Locale.ROOT);

        assertTrue(leftover.contains("G18 stays closed"));
        assertTrue(leftover.contains("honest SKIP") || leftover.contains("诚实 SKIP"));
        assertTrue(leftover.contains("NOT_CLAIMED") || leftover.contains("g7_close=NOT_CLAIMED"));
        assertTrue(leftover.contains("#140"));
        assertTrue(leftover.contains("#103"));
        assertTrue(leftover.contains("#113"));
        assertTrue(leftover.contains("#159"));
        assertTrue(leftover.contains("#175"));
        assertTrue(leftover.contains("Keep ALL sibling leftover rows")
                || leftover.contains("Keep **ALL** sibling leftover rows"));
        assertFalse(lower.contains("product g7 closed"));
        assertFalse(leftover.contains("产品 G7 已关闭"));
        assertFalse(leftover.contains("reopen G18"));

        for (String row : G7QueryExpenseTravelHonesty.README_LEFTOVER_ROWS) {
            assertTrue(index.contains("[" + row + "](./" + row + ".md)"), "dropped leftover row " + row);
        }
        assertTrue(index.contains("g7-query-expense-travel.md"));
        assertTrue(index.contains("g7-withdraw-browser.md"));
        assertTrue(index.contains("g7-withdraw-expense-travel.md"));
        assertFalse(index.toLowerCase(Locale.ROOT).contains("product g7 closed"));

        assertTrue(claim.contains("g7-query-expense-travel.md"));
        assertTrue(claim.contains("g7-query-expense-travel-honesty.md"));
        assertTrue(claim.contains("g7-withdraw-browser-honesty.md"));
        assertTrue(claim.contains("不宣称产品 G7 关闭"));
        assertFalse(claim.contains("产品 G7 已关闭"));

        assertTrue(query.contains("g7-query-expense-travel-honesty.md"));
        assertTrue(it.contains("/api/v1/p3/ledgers"));
        assertTrue(it.contains("/api/v1/p3/ledgers/" ));
        assertTrue(it.contains("/records"));
        assertTrue(it.contains("/api/v1/todos/pending"));
        assertTrue(it.contains("/api/v1/p3/todos"));
        assertTrue(it.contains("/api/v1/todos/initiated"));
        assertTrue(it.contains("expenseListDetailAndTodoQuerySurfaces"));
        assertTrue(it.contains("travelListDetailAndTodoQuerySurfaces"));
        assertTrue(it.contains("交通费"));
        assertTrue(it.contains("客户拜访"));
    }

    private static Path fixtureFile(String name) {
        Path first = Path.of("scripts/g7-query-expense-travel-honesty/fixtures/" + name);
        if (first.toFile().isFile()) {
            return first;
        }
        Path second = Path.of("../scripts/g7-query-expense-travel-honesty/fixtures/" + name);
        if (second.toFile().isFile()) {
            return second;
        }
        return Path.of("../../scripts/g7-query-expense-travel-honesty/fixtures/" + name);
    }

    private static Path repoFile(String relative) {
        Path nested = Path.of("..", relative);
        if (Files.isRegularFile(nested)) {
            return nested;
        }
        return Path.of(relative);
    }

    private static String readExisting(Path file) throws IOException {
        assertTrue(Files.isRegularFile(file), file.toAbsolutePath().toString());
        return Files.readString(file);
    }
}
