package one.nexus.event;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * G3 leftover honesty inventory <em>beyond #139 / #170</em>: Outbox
 * consume-idempotent harness may PASS; live multi-node Dispatcher and a
 * real process-kill crash window stay SKIP unless observed.
 *
 * <p>Does not redo #139 consume-idempotent Zonky suites or #170
 * {@link G3ClaimCrashWindowHonestyTest}. Does not rebuild
 * {@link OutboxDispatcher}. Does not change AuditPort same-TX rules.
 * Does not claim a product G3 close. G18 stays closed.
 */
public final class G3OutboxConsumeCrashHonesty {
    public static final String ROW_CONSUME = "g3-outbox-consume-idempotent";
    public static final String ROW_CRASH = "g3-claim-crash-window";
    public static final String BOTH_ROWS = ROW_CONSUME + "," + ROW_CRASH;
    public static final int REPLAY_STORM = 32;

    public record ConsumeHarness(
            String firstClaim,
            String replayClaim,
            int sideEffects,
            String verdict
    ) {}

    public record CrashWindow(
            boolean claimCommitted,
            boolean writeCommitted,
            String inboxStatus,
            String replayClaim,
            boolean sideEffectLost,
            String verdict
    ) {}

    public record DispatcherInventory(
            boolean secondJvmPresent,
            String secondJvmPid,
            boolean multiNodeObserved,
            String skipReason
    ) {
        public String multiNodeDispatcher() {
            return "SKIP";
        }
    }

    public record LiveCrashInventory(
            boolean liveCrashObserved,
            String skipReason
    ) {
        public String liveCrash() {
            return "SKIP";
        }
    }

    private G3OutboxConsumeCrashHonesty() {}

    /**
     * In-memory #139 contract: first claim is {@code FIRST} and increments
     * the side effect; later claims are {@code DUPLICATE} and skip it.
     * This is the leftover harness, not a Zonky storm redo.
     */
    public static ConsumeHarness proveConsumeIdempotent() {
        Map<String, String> claims = new LinkedHashMap<>();
        String key = "trace-g3-consume:inbox-1";
        String first = claim(claims, key);
        int sideEffects = 0;
        if ("FIRST".equals(first)) {
            sideEffects++;
        }
        String replay = "FIRST";
        for (int i = 0; i < REPLAY_STORM; i++) {
            replay = claim(claims, key);
            if ("FIRST".equals(replay)) {
                sideEffects++;
            }
        }
        boolean pass = "FIRST".equals(first)
                && "DUPLICATE".equals(replay)
                && sideEffects == 1;
        return new ConsumeHarness(first, replay, sideEffects, pass ? "PASS" : "FAIL");
    }

    /**
     * Same claim-then-write order as {@link G3ClaimCrashWindowHonesty}:
     * first claim commits, inbox write does not, replay is
     * {@code DUPLICATE}, side effect stays lost. Recorded as
     * {@code SIMULATED}, not a live process kill.
     */
    public static CrashWindow simulateClaimThenCrashBeforeWrite() {
        Map<String, String> claims = new LinkedHashMap<>();
        String key = "trace-g3-crash:inbox-1";
        String first = claim(claims, key);
        boolean writeCommitted = false;
        String inboxStatus = "PENDING";
        if (!"FIRST".equals(first) || writeCommitted) {
            throw new IllegalStateException("simulation must claim FIRST then crash before write");
        }
        String replay = claim(claims, key);
        boolean sideEffectLost = "DUPLICATE".equals(replay) && !writeCommitted;
        return new CrashWindow(true, false, inboxStatus, replay, sideEffectLost, "SIMULATED");
    }

    public static DispatcherInventory probeDispatchers() {
        String envPid = Optional.ofNullable(System.getenv("NEXUS_G3_SECOND_DISPATCHER_PID")).orElse("");
        boolean envObserved = "true".equalsIgnoreCase(System.getenv("NEXUS_G3_MULTI_NODE_OBSERVED"));
        long self = ProcessHandle.current().pid();
        boolean secondPresent = false;
        String secondPid = "";
        if (!envPid.isBlank()) {
            try {
                long pid = Long.parseLong(envPid.trim());
                if (pid != self && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
                    secondPresent = true;
                    secondPid = Long.toString(pid);
                }
            } catch (NumberFormatException ignored) {
                secondPresent = false;
            }
        }
        boolean observed = secondPresent && envObserved;
        String reason;
        if (!secondPresent) {
            reason = "one application JVM (pid=" + self
                    + "); surefire parent/fork is not a multi-node Dispatcher; no second JAR, no Kafka/bus, Docker forbidden";
        } else if (!observed) {
            reason = "second JVM pid=" + secondPid
                    + " is listed but a live multi-node Dispatcher / bus walk was not observed";
        } else {
            reason = "second JVM listed but this leftover still does not claim product G3 close";
        }
        return new DispatcherInventory(secondPresent, secondPid, observed, reason);
    }

    public static LiveCrashInventory probeLiveCrash() {
        boolean observed = "true".equalsIgnoreCase(System.getenv("NEXUS_G3_LIVE_CRASH_OBSERVED"));
        String reason = observed
                ? "live crash listed but this leftover still does not claim product G3 close"
                : "in-process claim-then-write crash simulation is not a live process kill; "
                        + "live_crash_observed=false; Docker forbidden";
        return new LiveCrashInventory(observed, reason);
    }

    public static Map<String, String> parseReport(Path file) {
        try {
            Map<String, String> values = new TreeMap<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                values.put(line.substring(0, eq), line.substring(eq + 1));
            }
            return values;
        } catch (IOException ex) {
            throw new IllegalArgumentException("report not readable: " + file, ex);
        }
    }

    public static void verify(Map<String, String> report) {
        if (report == null || report.isEmpty()) {
            throw new IllegalArgumentException("empty report");
        }
        for (String key : requiredKeys()) {
            if (!report.containsKey(key)) {
                throw new IllegalArgumentException("missing key " + key);
            }
        }
        require(report, "java_feature", "21");
        require(report, "boot_parent_version", "4.1.1");
        require(report, "boot_parent_match", "PASS");
        require(report, "docker_used", "false");
        require(report, "g18_status", "closed_unchanged");
        require(report, "audit_port_changed", "false");
        require(report, "outbox_dispatcher_redesigned", "false");
        require(report, "p0_m0_redone", "false");
        require(report, "g3_139_redone", "false");
        require(report, "g3_170_redone", "false");
        require(report, "g3_close", "NOT_CLAIMED");
        require(report, "leftover_rows_kept", "true");
        String rows = report.get("leftover_rows");
        if (rows == null || !rows.contains(ROW_CONSUME) || !rows.contains(ROW_CRASH)) {
            throw new IllegalArgumentException("leftover_rows must keep " + BOTH_ROWS);
        }
        require(report, "consume_idempotent", "PASS");
        require(report, "consume_idempotent_first", "FIRST");
        require(report, "consume_idempotent_replay", "DUPLICATE");
        require(report, "consume_idempotent_side_effects", "1");
        require(report, "crash_window_claim_committed", "true");
        require(report, "crash_window_write_committed", "false");
        require(report, "crash_window_inbox_status", "PENDING");
        require(report, "crash_window_replay_claim", "DUPLICATE");
        require(report, "crash_window_side_effect_lost", "true");
        requireVerdict(report, "consume_idempotent");
        requireCrashVerdict(report, "crash_window");
        requireVerdict(report, "live_crash");
        requireVerdict(report, "multi_node_dispatcher");
        refusePassWithout(report, "crash_window", "live_crash_observed", "true",
                "crash_window=PASS requires live_crash_observed=true");
        refusePassWithout(report, "live_crash", "live_crash_observed", "true",
                "live_crash=PASS requires live_crash_observed=true");
        refusePassWithout(report, "multi_node_dispatcher", "second_jvm_present", "true",
                "multi_node_dispatcher=PASS requires second_jvm_present=true");
        refusePassWithout(report, "multi_node_dispatcher", "multi_node_observed", "true",
                "multi_node_dispatcher=PASS requires multi_node_observed=true");
        if ("SIMULATED".equals(report.get("crash_window"))
                && !"true".equals(report.get("crash_window_side_effect_lost"))) {
            throw new IllegalArgumentException("SIMULATED crash window must record lost side effect");
        }
        requireSkipReason(report, "crash_window", "crash_window_skip_reason");
        requireSkipReason(report, "live_crash", "live_crash_skip_reason");
        requireSkipReason(report, "multi_node_dispatcher", "multi_node_skip_reason");
        if ("SKIP".equals(report.get("live_crash"))
                && "true".equals(report.get("live_crash_observed"))) {
            throw new IllegalArgumentException("SKIP cannot claim a live crash walk");
        }
        if ("SKIP".equals(report.get("multi_node_dispatcher"))
                && "true".equals(report.get("multi_node_observed"))) {
            throw new IllegalArgumentException("SKIP cannot claim a multi-node walk");
        }
        for (String key : report.keySet()) {
            String upper = key.toUpperCase(Locale.ROOT);
            if (upper.contains("PASSWORD") || upper.contains("SECRET") || upper.contains("PASSWD")) {
                throw new IllegalArgumentException("report must not contain password/secret material");
            }
        }
        String joined = String.join(" ", report.values()).toLowerCase(Locale.ROOT);
        if (joined.contains("product g3 closed") || joined.contains("g3_close=closed")) {
            throw new IllegalArgumentException("report must not claim product G3 closed");
        }
    }

    public static Map<String, String> honestSkipTemplate(
            ConsumeHarness consume,
            CrashWindow window,
            LiveCrashInventory liveCrash,
            DispatcherInventory nodes
    ) {
        Map<String, String> report = new LinkedHashMap<>();
        report.put("schema_version", "1");
        report.put("timestamp_utc", "");
        report.put("java_feature", "21");
        report.put("boot_parent_version", "4.1.1");
        report.put("boot_parent_match", "PASS");
        report.put("docker_used", "false");
        report.put("consume_idempotent", consume.verdict());
        report.put("consume_idempotent_first", consume.firstClaim());
        report.put("consume_idempotent_replay", consume.replayClaim());
        report.put("consume_idempotent_side_effects", Integer.toString(consume.sideEffects()));
        report.put("crash_window_claim_committed", Boolean.toString(window.claimCommitted()));
        report.put("crash_window_write_committed", Boolean.toString(window.writeCommitted()));
        report.put("crash_window_inbox_status", window.inboxStatus());
        report.put("crash_window_replay_claim", window.replayClaim());
        report.put("crash_window_side_effect_lost", Boolean.toString(window.sideEffectLost()));
        report.put("live_crash_observed", Boolean.toString(liveCrash.liveCrashObserved()));
        report.put("crash_window", window.verdict());
        report.put("crash_window_skip_reason",
                "in-process claim-then-write crash simulation; first claim committed, inbox UPDATE did not; replay DUPLICATE skips write; not a live process kill");
        report.put("live_crash", "SKIP");
        report.put("live_crash_skip_reason", liveCrash.skipReason());
        report.put("second_jvm_present", Boolean.toString(nodes.secondJvmPresent()));
        report.put("second_jvm_pid", nullToEmpty(nodes.secondJvmPid()));
        report.put("multi_node_observed", Boolean.toString(nodes.multiNodeObserved()));
        report.put("multi_node_dispatcher", "SKIP");
        report.put("multi_node_skip_reason", nodes.skipReason());
        report.put("leftover_rows_kept", "true");
        report.put("leftover_rows", BOTH_ROWS);
        report.put("g3_close", "NOT_CLAIMED");
        report.put("g18_status", "closed_unchanged");
        report.put("audit_port_changed", "false");
        report.put("outbox_dispatcher_redesigned", "false");
        report.put("p0_m0_redone", "false");
        report.put("g3_139_redone", "false");
        report.put("g3_170_redone", "false");
        return report;
    }

    private static String claim(Map<String, String> claims, String key) {
        if (claims.containsKey(key)) {
            return "DUPLICATE";
        }
        claims.put(key, "FIRST");
        return "FIRST";
    }

    private static String[] requiredKeys() {
        return new String[] {
                "schema_version", "timestamp_utc", "java_feature", "boot_parent_version",
                "boot_parent_match", "docker_used", "consume_idempotent",
                "consume_idempotent_first", "consume_idempotent_replay",
                "consume_idempotent_side_effects", "crash_window_claim_committed",
                "crash_window_write_committed", "crash_window_inbox_status",
                "crash_window_replay_claim", "crash_window_side_effect_lost",
                "live_crash_observed", "crash_window", "crash_window_skip_reason",
                "live_crash", "live_crash_skip_reason", "second_jvm_present",
                "second_jvm_pid", "multi_node_observed", "multi_node_dispatcher",
                "multi_node_skip_reason", "leftover_rows_kept", "leftover_rows",
                "g3_close", "g18_status", "audit_port_changed",
                "outbox_dispatcher_redesigned", "p0_m0_redone", "g3_139_redone",
                "g3_170_redone"
        };
    }

    private static void require(Map<String, String> report, String key, String expected) {
        String actual = report.get(key);
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(key + " must be " + expected + ", got " + actual);
        }
    }

    private static void requireVerdict(Map<String, String> report, String key) {
        String value = report.get(key);
        if (!"PASS".equals(value) && !"SKIP".equals(value) && !"FAIL".equals(value)) {
            throw new IllegalArgumentException(key + " must be PASS/SKIP/FAIL");
        }
    }

    private static void requireCrashVerdict(Map<String, String> report, String key) {
        String value = report.get(key);
        if (!"SIMULATED".equals(value) && !"SKIP".equals(value)
                && !"PASS".equals(value) && !"FAIL".equals(value)) {
            throw new IllegalArgumentException(key + " must be SIMULATED/SKIP/PASS/FAIL");
        }
    }

    private static void refusePassWithout(
            Map<String, String> report,
            String verdictKey,
            String requiredKey,
            String requiredValue,
            String message
    ) {
        if ("PASS".equals(report.get(verdictKey)) && !requiredValue.equals(report.get(requiredKey))) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireSkipReason(Map<String, String> report, String verdictKey, String reasonKey) {
        String verdict = report.get(verdictKey);
        if ("SKIP".equals(verdict) || "SIMULATED".equals(verdict)) {
            String reason = report.get(reasonKey);
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(verdictKey + " " + verdict + " needs " + reasonKey);
            }
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
