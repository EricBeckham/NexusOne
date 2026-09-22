package one.nexus.p2.idp;

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
 * G4 leftover honesty beyond #177 / #147: SPA mint / #177 honesty harness
 * stay always-green; live Keycloak CA / SPA-held token exchange is honest
 * SKIP when unreachable. Does not redo #108/#120/#125/#147/#152/#166/#177.
 * Does not claim product G4 close. G18 stays closed.
 */
class G4PkceSpaVerifierRemainderHonestyTest {

    @Test
    void rfc7636AndHonesty177HarnessStayAlwaysGreen() {
        assertTrue(G4PkceSpaVerifierRemainderHonesty.rfc7636AppendixB());
        assertTrue(G4PkceSpaVerifierRemainderHonesty.clientHeldDoesNotMintVerifier());
        assertTrue(G4PkceSpaVerifierRemainderHonesty.honesty177HarnessStaysPass());
        Pkce.Challenge minted = Pkce.s256(G4PkceSpaVerifierHonesty.RFC7636_VERIFIER);
        assertEquals(G4PkceSpaVerifierHonesty.RFC7636_CHALLENGE, minted.challenge());
        Pkce.Challenge held = Pkce.clientHeld(G4PkceSpaVerifierHonesty.RFC7636_CHALLENGE);
        assertTrue(Pkce.clientHeld(held));
        assertTrue(held.verifier() == null || held.verifier().isBlank());
    }

    @Test
    void liveProbeCannotClaimLiveWalks() {
        G4PkceSpaVerifierRemainderHonesty.Inventory inventory =
                G4PkceSpaVerifierRemainderHonesty.probeLive();
        assertEquals("SKIP", inventory.liveKeycloakPkce());
        assertEquals("SKIP", inventory.liveCaTrust());
        assertEquals("SKIP", inventory.humanLaptopClick());
        assertTrue(inventory.discoveryIsNotLiveExchange());
        assertTrue(inventory.honesty177IsNotLiveExchange());
        assertFalse(inventory.livePkceSkipReason().isBlank());
        assertFalse(inventory.liveCaSkipReason().isBlank());
        assertFalse(inventory.laptopClickSkipReason().isBlank());
        if (!inventory.discoveryReachable()) {
            assertFalse(inventory.tokenExchangeObserved());
            assertTrue(inventory.livePkceSkipReason().contains("does not start kc.sh")
                    || inventory.livePkceSkipReason().contains("not a live")
                    || inventory.livePkceSkipReason().contains("#177"));
        }

        Map<String, String> report = G4PkceSpaVerifierRemainderHonesty.honestSkipTemplate(inventory);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        G4PkceSpaVerifierRemainderHonesty.verify(report);
        assertEquals(G4PkceSpaVerifierRemainderHonesty.G4_CLOSE, report.get("g4_close"));
        assertEquals("PASS", report.get("honesty_177_harness"));
        assertEquals("true", report.get("leftover_rows_kept"));
        assertEquals("true", report.get("discovery_is_not_live_exchange"));
        assertEquals("true", report.get("honesty_177_is_not_live_exchange"));
        assertEquals("false", report.get("redo_147"));
        assertEquals("false", report.get("redo_177"));
        assertEquals("false", report.get("g4_108_120_125_147_152_166_177_redone"));
        assertEquals("false", report.get("p0_m0_redone"));
        assertEquals("closed_unchanged", report.get("g18_status"));
        assertEquals("false", report.get("kc_sh_started"));
        assertTrue(report.get("leftover_rows").contains("g4-pkce-spa-verifier"));
        assertTrue(report.get("leftover_rows").contains("g4-pkce-spa-verifier-honesty"));
        assertTrue(report.get("leftover_rows").contains("g4-pkce-spa-verifier-remainder-honesty"));
        assertTrue(report.get("leftover_rows").contains("g5-publishhook-ddl-honesty"));
        assertTrue(report.get("leftover_rows").contains("g17-gm-tls-nginx-handshake-honesty"));
        assertTrue(report.get("leftover_rows").contains("g9-sentinel-cluster-remainder-honesty"));
        assertTrue(report.get("leftover_rows").contains("g1-queryast-export-honesty"));
        assertTrue(report.get("leftover_rows").contains("g15-wx-dt-native-bridge-remainder"));
        assertTrue(report.get("leftover_rows").contains("g17-separation-leftover-honesty"));
        assertTrue(report.get("leftover_rows").contains("g2-ops-pg-dump-honesty"));
    }

    @Test
    void honestyGateAcceptsHonestSkipFixture() {
        G4PkceSpaVerifierRemainderHonesty.verify(
                G4PkceSpaVerifierRemainderHonesty.parseReport(fixture("honest-skip.env")));
    }

    @Test
    void honestyGateRejectsFakeLivePass() {
        Map<String, String> fake = new HashMap<>(
                G4PkceSpaVerifierRemainderHonesty.parseReport(fixture("fake-live-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G4PkceSpaVerifierRemainderHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("live_keycloak_pkce"));
    }

    @Test
    void honestyGateRejectsFakeCaPass() {
        Map<String, String> fake = new HashMap<>(
                G4PkceSpaVerifierRemainderHonesty.parseReport(fixture("fake-ca-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G4PkceSpaVerifierRemainderHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("live_ca_trust"));
    }

    @Test
    void honestyGateRejectsG4CloseClaim() {
        Map<String, String> fake = new HashMap<>(
                G4PkceSpaVerifierRemainderHonesty.parseReport(fixture("fake-g4-close.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G4PkceSpaVerifierRemainderHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("g4_close"));
    }

    @Test
    void honestyGateRejectsRedoOfClosedSlicesAndDroppedRows() {
        Map<String, String> fake = new HashMap<>(
                G4PkceSpaVerifierRemainderHonesty.parseReport(fixture("honest-skip.env")));
        fake.put("redo_177", "true");
        IllegalArgumentException redo177 = assertThrows(IllegalArgumentException.class,
                () -> G4PkceSpaVerifierRemainderHonesty.verify(fake));
        assertTrue(redo177.getMessage().contains("redo_177"));

        fake.put("redo_177", "false");
        fake.put("redo_147", "true");
        IllegalArgumentException redo147 = assertThrows(IllegalArgumentException.class,
                () -> G4PkceSpaVerifierRemainderHonesty.verify(fake));
        assertTrue(redo147.getMessage().contains("redo_147"));

        fake.put("redo_147", "false");
        fake.put("leftover_rows", "g4-pkce-spa-verifier");
        IllegalArgumentException rows = assertThrows(IllegalArgumentException.class,
                () -> G4PkceSpaVerifierRemainderHonesty.verify(fake));
        assertTrue(rows.getMessage().contains("leftover_rows"));
    }

    @Test
    void leftoverDocsKeepAllSiblingRowsIncluding147And177And217() throws IOException {
        String readme = readExisting(repoFile("docs/README.md"));
        for (String row : G4PkceSpaVerifierRemainderHonesty.README_ROWS) {
            assertTrue(readme.contains("[" + row + "](./" + row + ".md)"), "missing leftover row " + row);
        }
        assertTrue(readme.contains("[g4-pkce-spa-verifier](./g4-pkce-spa-verifier.md)"));
        assertTrue(readme.contains("[g4-pkce-spa-verifier-honesty](./g4-pkce-spa-verifier-honesty.md)"));
        assertTrue(readme.contains("[g4-pkce-spa-verifier-remainder-honesty](./g4-pkce-spa-verifier-remainder-honesty.md)"));
        assertTrue(readme.contains("[g5-publishhook-ddl-honesty](./g5-publishhook-ddl-honesty.md)"));
        assertTrue(readme.contains("[g17-gm-tls-nginx-handshake-honesty](./g17-gm-tls-nginx-handshake-honesty.md)"));
        assertTrue(readme.contains("[g9-sentinel-cluster-remainder-honesty](./g9-sentinel-cluster-remainder-honesty.md)"));
        assertTrue(readme.contains("[g1-queryast-export-honesty](./g1-queryast-export-honesty.md)"));
        assertTrue(readme.contains("[g15-wx-dt-native-bridge-remainder](./g15-wx-dt-native-bridge-remainder.md)"));
        assertTrue(readme.contains("[g17-separation-leftover-honesty](./g17-separation-leftover-honesty.md)"));
        assertFalse(readme.toLowerCase(Locale.ROOT).contains("product g4 closed"));
        assertFalse(readme.contains("产品 G4 已关闭"));

        String leftover = readExisting(repoFile("docs/g4-pkce-spa-verifier-remainder-honesty.md"));
        assertTrue(leftover.contains("Keep **ALL** sibling leftover rows")
                || leftover.contains("Keep ALL sibling leftover rows")
                || leftover.contains("Keep **all** sibling leftover rows"));
        assertTrue(leftover.contains("#147"));
        assertTrue(leftover.contains("#177"));
        assertTrue(leftover.contains("#209"));
        assertTrue(leftover.contains("#217"));
        assertTrue(leftover.contains("#210"));
        assertTrue(leftover.contains("#215"));
        assertTrue(leftover.contains("#220"));
        assertTrue(leftover.contains("#218"));
        assertTrue(leftover.contains("#216"));
        assertTrue(leftover.contains("does **not** claim product G4 close")
                || leftover.contains("Does **not** claim product G4 close")
                || leftover.contains("does **not** claim a product G4 close"));
        assertFalse(leftover.toLowerCase(Locale.ROOT).contains("product g4 is closed"));
        assertFalse(leftover.contains("产品 G4 已关闭"));
    }

    @Test
    void claimListG4RowStaysHonest() throws IOException {
        String text = readExisting(repoFile("docs/planning/团队领取任务清单_V1.1.md"));
        assertTrue(text.contains("g4-pkce-spa-verifier.md"));
        assertTrue(text.contains("g4-pkce-spa-verifier-honesty.md"));
        assertTrue(text.contains("g4-pkce-spa-verifier-remainder-honesty.md"));
        assertTrue(text.contains("g5-publishhook-ddl-honesty.md"));
        assertTrue(text.contains("g17-gm-tls-nginx-handshake-honesty.md"));
        assertTrue(text.contains("g9-sentinel-cluster-remainder-honesty.md"));
        assertTrue(text.contains("g1-queryast-export-honesty.md"));
        assertTrue(text.contains("g15-wx-dt-native-bridge-remainder.md"));
        assertTrue(text.contains("g17-separation-leftover-honesty.md"));
        assertTrue(text.contains("#147"));
        assertTrue(text.contains("#177"));
        assertTrue(text.contains("不宣称产品 G4 关闭"));
        assertTrue(text.contains("不要重做 #147"));
        assertTrue(text.contains("不要重开 G18"));
        assertFalse(text.contains("产品 G4 已关闭"));
    }

    private static Path fixture(String name) {
        Path first = Path.of("scripts/g4-pkce-spa-verifier-remainder-honesty/fixtures/" + name);
        if (first.toFile().isFile()) {
            return first;
        }
        return Path.of("../scripts/g4-pkce-spa-verifier-remainder-honesty/fixtures/" + name);
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
