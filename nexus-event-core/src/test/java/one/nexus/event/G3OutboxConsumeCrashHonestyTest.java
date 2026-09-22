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
import one.nexus.event.G3OutboxConsumeCrashHonesty.ConsumeHarness;
import one.nexus.event.G3OutboxConsumeCrashHonesty.CrashWindow;
import one.nexus.event.G3OutboxConsumeCrashHonesty.DispatcherInventory;
import one.nexus.event.G3OutboxConsumeCrashHonesty.LiveCrashInventory;
import org.junit.jupiter.api.Test;

/**
 * G3 leftover beyond #139 / #170: consume-idempotent harness PASS;
 * live multi-node Dispatcher / real crash window honest SKIP.
 * Does not redo #139 Zonky suites or #170 crash-window ITs.
 * Does not claim product G3 close. G18 stays closed.
 */
class G3OutboxConsumeCrashHonestyTest {

    @Test
    void consumeIdempotentHarnessPassesWithoutSecondSideEffect() {
        ConsumeHarness consume = G3OutboxConsumeCrashHonesty.proveConsumeIdempotent();
        assertEquals("FIRST", consume.firstClaim());
        assertEquals("DUPLICATE", consume.replayClaim());
        assertEquals(1, consume.sideEffects());
        assertEquals("PASS", consume.verdict());
    }

    @Test
    void simulatedCrashAfterClaimLeavesInboxPending() {
        CrashWindow window = G3OutboxConsumeCrashHonesty.simulateClaimThenCrashBeforeWrite();
        assertTrue(window.claimCommitted());
        assertFalse(window.writeCommitted());
        assertEquals("PENDING", window.inboxStatus());
        assertEquals("DUPLICATE", window.replayClaim());
        assertTrue(window.sideEffectLost());
        assertEquals("SIMULATED", window.verdict());
    }

    @Test
    void liveProbeCannotClaimCrashOrMultiNode() {
        LiveCrashInventory liveCrash = G3OutboxConsumeCrashHonesty.probeLiveCrash();
        DispatcherInventory nodes = G3OutboxConsumeCrashHonesty.probeDispatchers();
        assertEquals("SKIP", liveCrash.liveCrash());
        assertEquals("SKIP", nodes.multiNodeDispatcher());
        assertFalse(liveCrash.skipReason().isBlank());
        assertFalse(nodes.skipReason().isBlank());
        assertFalse(liveCrash.liveCrashObserved());
        assertFalse(nodes.multiNodeObserved());

        Map<String, String> report = G3OutboxConsumeCrashHonesty.honestSkipTemplate(
                G3OutboxConsumeCrashHonesty.proveConsumeIdempotent(),
                G3OutboxConsumeCrashHonesty.simulateClaimThenCrashBeforeWrite(),
                liveCrash,
                nodes);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        G3OutboxConsumeCrashHonesty.verify(report);
        assertEquals("PASS", report.get("consume_idempotent"));
        assertEquals("SIMULATED", report.get("crash_window"));
        assertEquals("SKIP", report.get("live_crash"));
        assertEquals("SKIP", report.get("multi_node_dispatcher"));
        assertEquals("NOT_CLAIMED", report.get("g3_close"));
        assertEquals("false", report.get("g3_139_redone"));
        assertEquals("false", report.get("g3_170_redone"));
        assertEquals("false", report.get("outbox_dispatcher_redesigned"));
        assertEquals("false", report.get("audit_port_changed"));
        assertEquals("false", report.get("p0_m0_redone"));
        assertEquals("closed_unchanged", report.get("g18_status"));
        assertEquals("true", report.get("leftover_rows_kept"));
        assertTrue(report.get("leftover_rows").contains("g3-outbox-consume-idempotent"));
        assertTrue(report.get("leftover_rows").contains("g3-claim-crash-window"));
    }

    @Test
    void honestyGateAcceptsHonestSkipFixture() {
        G3OutboxConsumeCrashHonesty.verify(
                G3OutboxConsumeCrashHonesty.parseReport(fixture("honest-skip.env")));
    }

    @Test
    void honestyGateRejectsFakeLiveCrashPass() {
        Map<String, String> fake = new HashMap<>(
                G3OutboxConsumeCrashHonesty.parseReport(fixture("fake-live-crash-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G3OutboxConsumeCrashHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("crash_window") || ex.getMessage().contains("live_crash"));
    }

    @Test
    void honestyGateRejectsFakeMultiNodePass() {
        Map<String, String> fake = new HashMap<>(
                G3OutboxConsumeCrashHonesty.parseReport(fixture("fake-multi-node-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G3OutboxConsumeCrashHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("multi_node_dispatcher"));
    }

    @Test
    void honestyGateRejectsG3CloseAndSiblingRedos() {
        IllegalArgumentException close = assertThrows(IllegalArgumentException.class,
                () -> G3OutboxConsumeCrashHonesty.verify(
                        G3OutboxConsumeCrashHonesty.parseReport(fixture("fake-g3-close.env"))));
        assertTrue(close.getMessage().contains("g3_close"));

        Map<String, String> fake = new HashMap<>(
                G3OutboxConsumeCrashHonesty.parseReport(fixture("honest-skip.env")));
        fake.put("g3_139_redone", "true");
        IllegalArgumentException redo139 = assertThrows(IllegalArgumentException.class,
                () -> G3OutboxConsumeCrashHonesty.verify(fake));
        assertTrue(redo139.getMessage().contains("g3_139_redone"));

        fake.put("g3_139_redone", "false");
        fake.put("g3_170_redone", "true");
        IllegalArgumentException redo170 = assertThrows(IllegalArgumentException.class,
                () -> G3OutboxConsumeCrashHonesty.verify(fake));
        assertTrue(redo170.getMessage().contains("g3_170_redone"));

        fake.put("g3_170_redone", "false");
        fake.put("leftover_rows", "g3-outbox-consume-idempotent");
        IllegalArgumentException rows = assertThrows(IllegalArgumentException.class,
                () -> G3OutboxConsumeCrashHonesty.verify(fake));
        assertTrue(rows.getMessage().contains("leftover_rows"));
    }

    @Test
    void leftoverDocsKeepBothG3RowsAndSiblings() throws IOException {
        String readme = readExisting(repoFile("docs/README.md"));
        assertTrue(readme.contains("[g3-outbox-consume-idempotent](./g3-outbox-consume-idempotent.md)"));
        assertTrue(readme.contains("[g3-claim-crash-window](./g3-claim-crash-window.md)"));
        assertTrue(readme.contains("[g3-outbox-consume-crash-honesty](./g3-outbox-consume-crash-honesty.md)"));
        assertTrue(readme.contains("[g17-separation-honesty](./g17-separation-honesty.md)"));
        assertTrue(readme.contains("[s0s1-live-redis-minio-isolation-honesty](./s0s1-live-redis-minio-isolation-honesty.md)"));
        assertTrue(readme.contains("[g6-recovery-port-dry-run-honesty](./g6-recovery-port-dry-run-honesty.md)"));
        assertTrue(readme.contains("[g4-p1-staging-audit-events-honesty](./g4-p1-staging-audit-events-honesty.md)"));
        assertTrue(readme.contains("[linux-arm-profile-leftover-honesty](./linux-arm-profile-leftover-honesty.md)"));
        assertTrue(readme.contains("[g7-query-expense-travel-honesty](./g7-query-expense-travel-honesty.md)"));
        assertTrue(readme.contains("[s0-secret-rotate-without-restart-honesty](./s0-secret-rotate-without-restart-honesty.md)"));
        assertTrue(readme.contains("[g15-wx-dt-native-bridge-honesty](./g15-wx-dt-native-bridge-honesty.md)"));
        assertTrue(readme.contains("[g4-hot-purge-90d-honesty](./g4-hot-purge-90d-honesty.md)"));
        assertTrue(readme.contains("[g1-fieldpolicy-mask-second-auth-honesty](./g1-fieldpolicy-mask-second-auth-honesty.md)"));
        assertTrue(readme.contains("[g10-identity-drift-honesty](./g10-identity-drift-honesty.md)"));
        assertTrue(readme.contains("[g15-native-bridge-honesty](./g15-native-bridge-honesty.md)"));
        assertTrue(readme.contains("[s0-secret-getenv-snapshot-honesty](./s0-secret-getenv-snapshot-honesty.md)"));
        assertTrue(readme.contains("[g5-browser-failed-publish](./g5-browser-failed-publish.md)"));
        assertTrue(readme.contains("[g9-live-multi-jvm-replica](./g9-live-multi-jvm-replica.md)"));
        assertTrue(readme.contains("[g6-recovery-port-honesty](./g6-recovery-port-honesty.md)"));
        assertFalse(readme.toLowerCase(Locale.ROOT).contains("product g3 closed"));
        assertFalse(readme.contains("产品 G3 已关闭"));

        String leftover = readExisting(repoFile("docs/g3-outbox-consume-crash-honesty.md"));
        assertTrue(leftover.contains("Keep both leftover rows") || leftover.contains("Keep **both** leftover rows"));
        assertTrue(leftover.contains("#139"));
        assertTrue(leftover.contains("#170"));
        assertTrue(leftover.contains("does **not** claim a product G3 close")
                || leftover.contains("Does **not** claim a product G3 close"));
        assertTrue(leftover.contains("does **not** redo #139") || leftover.contains("Does **not** redo #139"));
        assertTrue(leftover.contains("does **not** redo #170") || leftover.contains("Does **not** redo #170"));
        assertFalse(leftover.toLowerCase(Locale.ROOT).contains("product g3 is closed"));
        assertFalse(leftover.contains("产品 G3 已关闭"));
    }

    @Test
    void claimListG3RowStaysHonest() throws IOException {
        String text = readExisting(repoFile("docs/planning/团队领取任务清单_V1.1.md"));
        assertTrue(text.contains("g3-outbox-consume-idempotent.md"));
        assertTrue(text.contains("g3-claim-crash-window.md"));
        assertTrue(text.contains("g3-outbox-consume-crash-honesty.md"));
        assertTrue(text.contains("#139"));
        assertTrue(text.contains("#170"));
        assertTrue(text.contains("不要重做 #139"));
        assertTrue(text.contains("不宣称产品 G3 关闭"));
        assertTrue(text.contains("g17-separation-honesty.md"));
        assertTrue(text.contains("s0s1-live-redis-minio-isolation-honesty.md"));
        assertTrue(text.contains("g6-recovery-port-dry-run-honesty.md"));
        assertTrue(text.contains("g4-p1-staging-audit-events-honesty.md"));
        assertTrue(text.contains("linux-arm-profile-leftover-honesty.md"));
        assertTrue(text.contains("g7-query-expense-travel-honesty.md"));
        assertTrue(text.contains("s0-secret-rotate-without-restart-honesty.md"));
        assertTrue(text.contains("g15-wx-dt-native-bridge-honesty.md"));
        assertTrue(text.contains("g4-hot-purge-90d-honesty.md"));
        assertTrue(text.contains("不要重开 G18"));
        assertFalse(text.contains("产品 G3 已关闭"));
    }

    private static Path fixture(String name) {
        Path first = Path.of("scripts/g3-outbox-consume-crash-honesty/fixtures/" + name);
        if (first.toFile().isFile()) {
            return first;
        }
        return Path.of("../scripts/g3-outbox-consume-crash-honesty/fixtures/" + name);
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
