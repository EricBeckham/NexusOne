package one.nexus.p4.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * XB12: the XB wave needs no Flyway migration. Upgrade steps/facts (XB6/XB7) are computed from
 * market.installation + app versions; the entitlement summary (XB10) reads market.subscription, the SKU
 * entitlements and platform.entitlement_projection (V100) as they are. Pins those columns and the absence
 * of new storage, so a future need shows up here instead of as an ad-hoc migration.
 */
class XbWaveSchemaContractTest {
    private static final Path MARKET = Path.of("src/main/resources/db/migration");
    private static final Path P1_V100 = Path.of("../nexus-p1-tenant/src/main/resources/db/migration/V100__p1_tenant.sql");

    private static String read(Path path) throws IOException {
        return Files.readString(path).toLowerCase(Locale.ROOT);
    }

    private static String table(String sql, String name) {
        int start = sql.indexOf("create table " + name + " (");
        assertTrue(start >= 0, name + " must exist");
        return sql.substring(start, sql.indexOf(");", start));
    }

    @Test
    void entitlementSummaryReadsTheExistingV100ProjectionColumns() throws IOException {
        String projection = table(read(P1_V100), "platform.entitlement_projection");
        assertTrue(projection.contains("tenant_id         uuid primary key"), "one projection row per tenant (XB9 boundary)");
        for (String column : List.of("source", "sku_code", "subscription_id", "projected_at", "payload", "version")) {
            assertTrue(Pattern.compile("\\b" + column + "\\b").matcher(projection).find(), column);
        }
        assertTrue(projection.contains("'default_pack', 'subscription'"), "projected=true only for SUBSCRIPTION sources");
    }

    @Test
    void upgradeAndSubscriptionInputsExistInV600() throws IOException {
        String v600 = read(MARKET.resolve("V600__p4_market.sql"));
        String subscription = table(v600, "market.subscription");
        for (String column : List.of("id", "tenant_id", "sku_code", "status", "started_at", "ended_at")) {
            assertTrue(Pattern.compile("\\b" + column + "\\b").matcher(subscription).find(), "subscription." + column);
        }
        assertTrue(table(v600, "market.sku").contains("entitlements jsonb not null"));
        String installation = table(v600, "market.installation");
        assertTrue(installation.contains("version_id uuid not null"));
        assertFalse(installation.contains("step"), "no upgrade step state on installations");
    }

    @Test
    void upgradeStateOnlyInTheControlledRecordTableAndNoMoney() throws IOException {
        Pattern upgradeStorage = Pattern.compile("(upgrade_step\\w*|upgrade_fact\\w*|upgrade_run\\w*)");
        Pattern money = Pattern.compile("\\b(price\\w*|amount\\w*|invoice\\w*|coupon\\w*|billing\\w*|payment\\w*|fee\\w*)\\b");
        String allowed = "V709__market_upgrade_step_record.sql";
        try (Stream<Path> roots = Stream.of(Path.of("..")).flatMap(XbWaveSchemaContractTest::modules)) {
            List<Path> files = roots.toList();
            assertTrue(files.size() >= 30, "all module migrations must be scanned, found " + files.size());
            for (Path file : files) {
                if (file.getFileName().toString().equals(allowed)) continue;
                String sql = read(file);
                var hit = upgradeStorage.matcher(sql);
                boolean found = hit.find();
                assertFalse(found, () -> file.getFileName() + " stores upgrade state: " + hit.group());
            }
        }
        // DPO-L6-pre: the one place upgrade steps are stored must keep XB honesty — no UNKNOWN or default status
        // (absence is UNKNOWN), no facts table, and every row names an operator and a reason.
        String record = table(read(MARKET.resolve(allowed)), "market.upgrade_step_record");
        assertTrue(record.contains("check (status in ('active', 'done', 'failed'))"), record);
        assertFalse(record.contains("unknown"), "UNKNOWN is never stored");
        assertFalse(Pattern.compile("status varchar\\(16\\) not null default").matcher(record).find(), "no default status");
        assertTrue(record.contains("recorded_by uuid not null"), record);
        assertTrue(record.contains("reason text not null"), record);
        assertFalse(read(MARKET.resolve(allowed)).contains("upgrade_fact"), "facts stay undeclared");
        var recordMoney = money.matcher(read(MARKET.resolve(allowed)));
        assertFalse(recordMoney.find(), "upgrade records carry no money");
        for (Path file : List.of(MARKET.resolve("V600__p4_market.sql"), MARKET.resolve("V601__market_product_definition.sql"),
                MARKET.resolve("V705__market_installation_product_pin.sql"),
                MARKET.resolve("V707__market_print_mapping.sql"),
                MARKET.resolve("V708__market_print_template.sql"),
                // P2 #601: activation applications live next to market data and must stay payment-free too.
                Path.of("../nexus-p1-tenant/src/main/resources/db/migration/V706__tenant_self_reg_and_activation_application.sql"))) {
            var hit = money.matcher(read(file));
            boolean found = hit.find();
            assertFalse(found, () -> file.getFileName() + " has a money column: " + hit.group());
        }
        var projectionMoney = money.matcher(table(read(P1_V100), "platform.entitlement_projection"));
        assertFalse(projectionMoney.find(), "the entitlement projection carries no money fields");
    }

    @Test
    void highestMigrationVersionIsTheKnownOne() throws IOException {
        try (Stream<Path> roots = Stream.of(Path.of("..")).flatMap(XbWaveSchemaContractTest::modules)) {
            int max = roots.map(path -> path.getFileName().toString())
                    .map(name -> Integer.parseInt(name.substring(1, name.indexOf("__"))))
                    .max(Integer::compare).orElseThrow();
            // XB added none (V705 at XB12). V706 is P2 #601 (tenant self-registration + activation applications).
            // V707 is DPO-L2 (durable print mapping + revision history). V708 is DPO-L4-pre (print template metadata).
            // V709 is DPO-L6-pre (operator-recorded upgrade steps).
            // A new migration must update this pin on purpose, taking the next global version.
            assertEquals(709, max,"unexpected new migration; take the next global version in a dedicated schema card");
        }
    }

    private static Stream<Path> modules(Path repo) {
        try {
            return Files.list(repo)
                    .filter(dir -> dir.getFileName().toString().startsWith("nexus-"))
                    .map(dir -> dir.resolve("src/main/resources/db/migration"))
                    .filter(Files::isDirectory)
                    .flatMap(dir -> {
                        try {
                            return Files.list(dir).filter(file -> file.getFileName().toString().matches("V\\d+__.*\\.sql")).toList().stream();
                        } catch (IOException ex) {
                            throw new IllegalStateException(ex);
                        }
                    });
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}