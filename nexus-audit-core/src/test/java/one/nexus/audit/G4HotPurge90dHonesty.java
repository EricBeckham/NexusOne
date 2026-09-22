package one.nexus.audit;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import one.nexus.common.audit.AuditCategory;
import one.nexus.common.audit.AuditEvent;
import one.nexus.common.id.UuidV7;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;

/**
 * G4 leftover honesty inventory for the remaining 90-day hot-purge
 * window after #123. In-memory window policy may PASS. Live
 * {@code :5432} / JAR / Quartz / MinIO remaining window is SKIP unless
 * those are present <em>and</em> a live purge walk was observed. Does
 * not redo #123 / #136 / #147 / #152 / #156 / #166. Does not DROP
 * {@code platform.audit_record}. Does not change AuditPort same-TX
 * rules. Not a product G4 close.
 */
public final class G4HotPurge90dHonesty {
    public static final String HOST = "127.0.0.1";
    public static final int LIVE_PG_PORT = 5432;
    public static final int LIVE_JAR_PORT = 8080;
    public static final int LIVE_MINIO_PORT = 9000;
    public static final Instant FIXED_NOW = Instant.parse("2026-09-22T18:00:00Z");

    public record WindowPolicy(
            Duration hotRetention,
            Duration archiveRetention,
            boolean youngEligible,
            boolean archived91DayEligible,
            boolean archiveOnly366EligibleWithoutCold,
            boolean dropTable
    ) {
        public String verdict() {
            boolean pass = Duration.ofDays(90).equals(hotRetention)
                    && Duration.ofDays(365).equals(archiveRetention)
                    && !youngEligible
                    && archived91DayEligible
                    && !archiveOnly366EligibleWithoutCold
                    && !dropTable;
            return pass ? "PASS" : "FAIL";
        }
    }

    public record LiveInventory(
            boolean liveDbPresent,
            boolean liveJarPresent,
            boolean liveMinioPresent,
            boolean livePurgeObserved,
            boolean liveQuartzObserved,
            boolean liveMinioColdObserved,
            String livePurgeSkipReason,
            String liveQuartzSkipReason,
            String liveMinioSkipReason
    ) {
        public String livePurgeWindow() {
            return "SKIP";
        }

        public String liveQuartz() {
            return "SKIP";
        }

        public String liveMinioCold() {
            return "SKIP";
        }
    }

    private G4HotPurge90dHonesty() {}

    public static WindowPolicy proveWindowPolicy() {
        Instant now = FIXED_NOW;
        var hot = new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
        UUID tenant = UuidV7.generate();
        hot.append(agedExport(tenant, "0199a1c0-0000-7000-8000-00000000h091",
                now.minus(AuditRetentionPolicy.HOT_RETENTION).minusSeconds(60)));
        hot.append(agedExport(tenant, "0199a1c0-0000-7000-8000-00000000h010",
                now.minusSeconds(3600)));
        hot.append(agedExport(tenant, "0199a1c0-0000-7000-8000-00000000h366",
                now.minus(AuditRetentionPolicy.ARCHIVE_RETENTION).minusSeconds(60)));
        ChainedAuditEvent aged91 = hot.chain().get(0);
        ChainedAuditEvent young = hot.chain().get(1);
        ChainedAuditEvent aged366 = hot.chain().get(2);
        Set<UUID> archived = Set.of(aged91.event().eventId(), aged366.event().eventId());
        return new WindowPolicy(
                AuditRetentionPolicy.HOT_RETENTION,
                AuditRetentionPolicy.ARCHIVE_RETENTION,
                AuditHotPurgePolicy.eligible(young, now, archived, null),
                AuditHotPurgePolicy.eligible(aged91, now, archived, null),
                AuditHotPurgePolicy.eligible(aged366, now, archived, null),
                false);
    }

    public static LiveInventory probeLive() {
        boolean db = portOpen(HOST, LIVE_PG_PORT);
        boolean jar = portOpen(HOST, LIVE_JAR_PORT);
        boolean minio = portOpen(HOST, LIVE_MINIO_PORT);
        boolean purgeObserved = envTrue("NEXUS_G4_LIVE_PURGE_OBSERVED");
        boolean quartzObserved = envTrue("NEXUS_G4_LIVE_QUARTZ_OBSERVED");
        boolean minioColdObserved = envTrue("NEXUS_G4_LIVE_MINIO_COLD_OBSERVED");
        String purgeReason;
        if (envTrue("NEXUS_G4_HOT_PURGE_SKIP_LIVE")) {
            purgeReason = "NEXUS_G4_HOT_PURGE_SKIP_LIVE=1; live :5432 / JAR purge window not walked; "
                    + "Zonky #123 is not a live purge PASS; Docker forbidden";
        } else if (!db && !jar) {
            purgeReason = "no live PostgreSQL on " + HOST + ":" + LIVE_PG_PORT
                    + " and no product JAR on " + HOST + ":" + LIVE_JAR_PORT
                    + "; Zonky #123 is not a live purge PASS; Docker forbidden";
        } else if (!db) {
            purgeReason = "product JAR may be present but no live PostgreSQL on " + HOST + ":"
                    + LIVE_PG_PORT + "; live 90-day prefix DELETE not observed; Docker forbidden";
        } else if (!jar) {
            purgeReason = "PostgreSQL listener present on " + HOST + ":" + LIVE_PG_PORT
                    + " but no product JAR on " + HOST + ":" + LIVE_JAR_PORT
                    + "; live purge Job not observed; Docker forbidden";
        } else if (!purgeObserved) {
            purgeReason = "live :5432 and JAR listeners present but a live 90-day prefix DELETE "
                    + "was not observed; Zonky #123 is not a live purge PASS; Docker forbidden";
        } else {
            purgeReason = "live purge listed but this leftover still does not claim product G4 close";
        }
        String quartzReason = quartzObserved
                ? "live Quartz listed but schedule-enabled stays false by default; not a product G4 close"
                : "nexus.audit.hot-purge.schedule-enabled defaults false; live Quartz 03:00 UTC "
                        + "walk not observed; IsolatedQuartzJob is tenant-only";
        String minioReason = minioColdObserved
                ? "live MinIO cold listed but this leftover still does not claim product G4 close"
                : (minio
                        ? "MinIO listener present on " + HOST + ":" + LIVE_MINIO_PORT
                            + " but a live cold-copy drill was not observed"
                        : "no live MinIO on " + HOST + ":" + LIVE_MINIO_PORT
                            + "; fake / counting storage is not a live cold drill");
        return new LiveInventory(
                db, jar, minio, purgeObserved, quartzObserved, minioColdObserved,
                purgeReason, quartzReason, minioReason);
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
        require(report, "hot_table_dropped", "false");
        require(report, "p0_m0_redone", "false");
        require(report, "g4_123_136_147_152_156_166_redone", "false");
        require(report, "g4_close", "NOT_CLAIMED");
        requireVerdict(report, "window_policy");
        requireVerdict(report, "live_purge_window");
        requireVerdict(report, "live_quartz");
        requireVerdict(report, "live_minio_cold");
        if (!"PASS".equals(report.get("window_policy"))) {
            throw new IllegalArgumentException("window_policy must be PASS (90d + 365d archive window, no DROP)");
        }
        refusePassWithout(report, "live_purge_window", "live_db_present", "true",
                "live_purge_window=PASS requires live_db_present=true");
        refusePassWithout(report, "live_purge_window", "live_jar_present", "true",
                "live_purge_window=PASS requires live_jar_present=true");
        refusePassWithout(report, "live_purge_window", "live_purge_observed", "true",
                "live_purge_window=PASS requires live_purge_observed=true");
        refusePassWithout(report, "live_quartz", "live_quartz_observed", "true",
                "live_quartz=PASS requires live_quartz_observed=true");
        refusePassWithout(report, "live_minio_cold", "live_minio_present", "true",
                "live_minio_cold=PASS requires live_minio_present=true");
        refusePassWithout(report, "live_minio_cold", "live_minio_cold_observed", "true",
                "live_minio_cold=PASS requires live_minio_cold_observed=true");
        requireSkipReason(report, "live_purge_window", "live_purge_window_skip_reason");
        requireSkipReason(report, "live_quartz", "live_quartz_skip_reason");
        requireSkipReason(report, "live_minio_cold", "live_minio_cold_skip_reason");
        if ("SKIP".equals(report.get("live_purge_window"))
                && "true".equals(report.get("live_purge_observed"))) {
            throw new IllegalArgumentException("SKIP cannot claim a live purge walk");
        }
        if ("SKIP".equals(report.get("live_quartz"))
                && "true".equals(report.get("live_quartz_observed"))) {
            throw new IllegalArgumentException("SKIP cannot claim a live Quartz walk");
        }
        if ("SKIP".equals(report.get("live_minio_cold"))
                && "true".equals(report.get("live_minio_cold_observed"))) {
            throw new IllegalArgumentException("SKIP cannot claim a live MinIO cold walk");
        }
        for (String key : report.keySet()) {
            String upper = key.toUpperCase(Locale.ROOT);
            if (upper.contains("PASSWORD") || upper.contains("SECRET") || upper.contains("PASSWD")) {
                throw new IllegalArgumentException("report must not contain password/secret material");
            }
        }
        String joined = String.join(" ", report.values()).toLowerCase(Locale.ROOT);
        if (joined.contains("product g4 closed") || joined.contains("g4_close=closed")) {
            throw new IllegalArgumentException("report must not claim product G4 closed");
        }
    }

    public static Map<String, String> honestSkipTemplate(WindowPolicy window, LiveInventory live) {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(live, "live");
        Map<String, String> report = new LinkedHashMap<>();
        report.put("schema_version", "1");
        report.put("timestamp_utc", "");
        report.put("java_feature", "21");
        report.put("boot_parent_version", "4.1.1");
        report.put("boot_parent_match", "PASS");
        report.put("docker_used", "false");
        report.put("window_policy", window.verdict());
        report.put("hot_retention_days", Long.toString(window.hotRetention().toDays()));
        report.put("archive_retention_days", Long.toString(window.archiveRetention().toDays()));
        report.put("hot_table_dropped", "false");
        report.put("live_db_present", Boolean.toString(live.liveDbPresent()));
        report.put("live_jar_present", Boolean.toString(live.liveJarPresent()));
        report.put("live_minio_present", Boolean.toString(live.liveMinioPresent()));
        report.put("live_purge_observed", Boolean.toString(live.livePurgeObserved()));
        report.put("live_purge_window", "SKIP");
        report.put("live_purge_window_skip_reason", live.livePurgeSkipReason());
        report.put("live_quartz_observed", Boolean.toString(live.liveQuartzObserved()));
        report.put("live_quartz", "SKIP");
        report.put("live_quartz_skip_reason", live.liveQuartzSkipReason());
        report.put("live_minio_cold_observed", Boolean.toString(live.liveMinioColdObserved()));
        report.put("live_minio_cold", "SKIP");
        report.put("live_minio_cold_skip_reason", live.liveMinioSkipReason());
        report.put("g4_close", "NOT_CLAIMED");
        report.put("g18_status", "closed_unchanged");
        report.put("audit_port_changed", "false");
        report.put("p0_m0_redone", "false");
        report.put("g4_123_136_147_152_156_166_redone", "false");
        return report;
    }

    private static String[] requiredKeys() {
        return new String[] {
                "schema_version", "timestamp_utc", "java_feature", "boot_parent_version",
                "boot_parent_match", "docker_used", "window_policy", "hot_retention_days",
                "archive_retention_days", "hot_table_dropped", "live_db_present",
                "live_jar_present", "live_minio_present", "live_purge_observed",
                "live_purge_window", "live_purge_window_skip_reason",
                "live_quartz_observed", "live_quartz", "live_quartz_skip_reason",
                "live_minio_cold_observed", "live_minio_cold", "live_minio_cold_skip_reason",
                "g4_close", "g18_status", "audit_port_changed", "p0_m0_redone",
                "g4_123_136_147_152_156_166_redone"
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
        if ("SKIP".equals(report.get(verdictKey))) {
            String reason = report.get(reasonKey);
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(verdictKey + " SKIP needs " + reasonKey);
            }
        }
    }

    private static boolean portOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 200);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private static boolean envTrue(String name) {
        return "true".equalsIgnoreCase(System.getenv(name))
                || "1".equals(System.getenv(name));
    }

    private static AuditEvent agedExport(UUID tenant, String trace, Instant occurredAt) {
        return new AuditEvent(
                UuidV7.generate(),
                AuditEvent.VERSION,
                occurredAt,
                AuditCategory.EXPORT,
                AuditCaptures.EXPORT_SUBMITTED,
                tenant,
                tenant,
                tenant,
                false,
                "g4 hot-purge honesty window",
                trace,
                trace,
                null,
                AuditEvent.OUTCOME_ACCEPTED,
                "export",
                "ledger.export.csv",
                Map.of("action", "honesty-window"));
    }
}
