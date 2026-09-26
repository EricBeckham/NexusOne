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
    void noMigrationStoresUpgradeStepsFactsOrMoney() throws IOException {
        Pattern upgradeStorage = Pattern.compile("\\b(upgrade_step\\w*|upgrade_fact\\w*|upgrade_run\\w*)\\b");
        Pattern money = Pattern.compile("\\b(price\\w*|amount\\w*|invoice\\w*|coupon\\w*|billing\\w*|payment\\w*|fee\\w*)\\b");
        try (Stream<Path> roots = Stream.of(Path.of("..")).flatMap(XbWaveSchemaContractTest::modules)) {
            List<Path> files = roots.toList();
            assertTrue(files.size() >= 30, "all module migrations must be scanned, found " + files.size());
            for (Path file : files) {
                String sql = read(file);
                var hit = upgradeStorage.matcher(sql);
                boolean found = hit.find();
                assertFalse(found, () -> file.getFileName() + " stores upgrade state: " + hit.group());
            }
        }
        for (Path file : List.of(MARKET.resolve("V600__p4_market.sql"), MARKET.resolve("V601__market_product_definition.sql"),
                MARKET.resolve("V705__market_installation_product_pin.sql"))) {
            var hit = money.matcher(read(file));
            boolean found = hit.find();
            assertFalse(found, () -> file.getFileName() + " has a money column: " + hit.group());
        }
        var projectionMoney = money.matcher(table(read(P1_V100), "platform.entitlement_projection"));
        assertFalse(projectionMoney.find(), "the entitlement projection carries no money fields");
    }

    @Test
    void highestMigrationVersionIsStillV705() throws IOException {
        try (Stream<Path> roots = Stream.of(Path.of("..")).flatMap(XbWaveSchemaContractTest::modules)) {
            int max = roots.map(path -> path.getFileName().toString())
                    .map(name -> Integer.parseInt(name.substring(1, name.indexOf("__"))))
                    .max(Integer::compare).orElseThrow();
            assertEquals(705, max, "XB adds no migration; a real need goes through XB12 with the next global version");
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