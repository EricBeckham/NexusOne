package one.nexus.event;

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
import one.nexus.event.G3ClaimCrashWindowHonesty.CrashWindow;
import one.nexus.event.G3ClaimCrashWindowHonesty.DispatcherInventory;
import org.junit.jupiter.api.Test;

/**
 * G3 leftover beyond #139: honesty inventory for the claim-then-write crash
 * window and multi-node Dispatcher remainder. Does not redo consume-idempotent
 * stress, does not rebuild OutboxDispatcher, and does not claim product G3.
 */
class G3ClaimCrashWindowHonestyTest {

    @Test
    void simulatedCrashAfterClaimLeavesInboxPending() {
        CrashWindow window = G3ClaimCrashWindowHonesty.simulateClaimThenCrashBeforeWrite();
        assertTrue(window.claimCommitted());
        assertFalse(window.writeCommitted());
        assertEquals("PENDING", window.inboxStatus());
        assertEquals("DUPLICATE", window.replayClaim());
        assertTrue(window.sideEffectLost());
        assertEquals("SIMULATED", window.verdict());
    }

    @Test
    void liveProbeCannotClaimMultiNodeDispatcher() {
        DispatcherInventory nodes = G3ClaimCrashWindowHonesty.probeDispatchers();
        assertEquals("SKIP", nodes.multiNodeDispatcher());
        assertFalse(nodes.skipReason().isBlank());
        assertFalse(nodes.multiNodeObserved());
        assertTrue(nodes.skipReason().contains("surefire") || nodes.skipReason().contains("one application JVM"));
    }

    @Test
    void honestyGateAcceptsSimulatedCrashAndSkipFixture() {
        CrashWindow window = G3ClaimCrashWindowHonesty.simulateClaimThenCrashBeforeWrite();
        DispatcherInventory nodes = G3ClaimCrashWindowHonesty.probeDispatchers();
        Map<String, String> report = G3ClaimCrashWindowHonesty.honestSkipTemplate(window, nodes);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        G3ClaimCrashWindowHonesty.verify(report);
        assertEquals("NOT_CLAIMED", report.get("g3_close"));
        assertEquals("false", report.get("g3_139_redone"));
        assertEquals("SIMULATED", report.get("crash_window"));
        assertEquals("SKIP", report.get("multi_node_dispatcher"));
        G3ClaimCrashWindowHonesty.verify(G3ClaimCrashWindowHonesty.parseReport(fixture("honest-skip.env")));
    }

    @Test
    void honestyGateRejectsFakeCrashWindowPass() {
        Map<String, String> fake = new HashMap<>(G3ClaimCrashWindowHonesty.parseReport(fixture("fake-crash-window-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G3ClaimCrashWindowHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("crash_window"));
    }

    @Test
    void honestyGateRejectsFakeMultiNodePass() {
        Map<String, String> fake = new HashMap<>(G3ClaimCrashWindowHonesty.parseReport(fixture("fake-multi-node-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G3ClaimCrashWindowHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("multi_node_dispatcher"));
    }

    @Test
    void honestyGateRejectsG3CloseClaim() {
        Map<String, String> fake = new HashMap<>(G3ClaimCrashWindowHonesty.parseReport(fixture("honest-skip.env")));
        fake.put("g3_close", "CLOSED");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G3ClaimCrashWindowHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("g3_close"));
    }

    @Test
    void leftoverDocsKeepBothG3RowsAndSiblings() throws IOException {
        String readme = readExisting(repoFile("docs/README.md"));
        assertTrue(readme.contains("[g3-outbox-consume-idempotent](./g3-outbox-consume-idempotent.md)"));
        assertTrue(readme.contains("[g3-claim-crash-window](./g3-claim-crash-window.md)"));
        assertTrue(readme.contains("g9-live-multi-jvm-replica"));
        assertTrue(readme.contains("g6-recovery-port-honesty"));
        assertTrue(readme.contains("g2-h5-every-page"));
        assertTrue(readme.contains("linux-arm-profile-honesty"));
        assertTrue(readme.contains("g10-file-reconcile-honesty"));
        assertTrue(readme.contains("g4-export-wizard-deny"));
        assertTrue(readme.contains("g15-wx-dt-prod-gap-honesty"));
        assertFalse(readme.toLowerCase(Locale.ROOT).contains("product g3 closed"));
        assertFalse(readme.contains("产品 G3 已关闭"));

        String leftover = readExisting(repoFile("docs/g3-claim-crash-window.md"));
        assertTrue(leftover.contains("Keep both leftover rows"));
        assertTrue(leftover.contains("#139"));
        assertTrue(leftover.contains("does **not** claim a product G3 close")
                || leftover.contains("Does **not** claim a product G3 close"));
        assertTrue(leftover.contains("does **not** redo #139") || leftover.contains("Do not redo #139"));
        assertFalse(leftover.toLowerCase(Locale.ROOT).contains("product g3 is closed"));
    }

    @Test
    void claimListG3RowStaysHonest() throws IOException {
        String text = readExisting(repoFile("docs/planning/团队领取任务清单_V1.1.md"));
        assertTrue(text.contains("g3-outbox-consume-idempotent.md"));
        assertTrue(text.contains("g3-claim-crash-window.md"));
        assertTrue(text.contains("#139"));
        assertTrue(text.contains("不要重做 #139"));
        assertTrue(text.contains("不宣称产品 G3 关闭"));
        assertTrue(text.contains("linux-arm-profile-honesty.md"));
        assertTrue(text.contains("g10-file-reconcile-honesty.md"));
        assertTrue(text.contains("g4-export-wizard-deny.md"));
        assertTrue(text.contains("g15-wx-dt-prod-gap-honesty.md"));
        assertFalse(text.contains("产品 G3 已关闭"));
    }

    private static Path fixture(String name) {
        Path first = Path.of("scripts/g3-claim-crash-window/fixtures/" + name);
        if (first.toFile().isFile()) {
            return first;
        }
        return Path.of("../scripts/g3-claim-crash-window/fixtures/" + name);
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
