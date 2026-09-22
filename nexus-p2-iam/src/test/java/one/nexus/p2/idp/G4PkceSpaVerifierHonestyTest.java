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
 * G4 leftover honesty beyond #147: SPA mint / client-held challenge stay
 * always-green; live Keycloak CA / SPA-held token exchange is honest SKIP
 * when unreachable. Does not redo #108/#120/#125/#147/#152/#166.
 */
class G4PkceSpaVerifierHonestyTest {

    @Test
    void rfc7636AppendixBAndClientHeldStayAlwaysGreen() {
        assertTrue(G4PkceSpaVerifierHonesty.rfc7636AppendixB());
        assertTrue(G4PkceSpaVerifierHonesty.clientHeldDoesNotMintVerifier());
        Pkce.Challenge minted = Pkce.s256(G4PkceSpaVerifierHonesty.RFC7636_VERIFIER);
        assertEquals(G4PkceSpaVerifierHonesty.RFC7636_CHALLENGE, minted.challenge());
        Pkce.Challenge held = Pkce.clientHeld(G4PkceSpaVerifierHonesty.RFC7636_CHALLENGE);
        assertTrue(Pkce.clientHeld(held));
        assertTrue(held.verifier() == null || held.verifier().isBlank());
    }

    @Test
    void liveProbeCannotClaimLiveWalks() {
        G4PkceSpaVerifierHonesty.Inventory inventory = G4PkceSpaVerifierHonesty.probeLive();
        assertEquals("SKIP", inventory.liveKeycloakPkce());
        assertEquals("SKIP", inventory.liveCaTrust());
        assertEquals("SKIP", inventory.humanLaptopClick());
        assertTrue(inventory.discoveryIsNotLiveExchange());
        assertFalse(inventory.livePkceSkipReason().isBlank());
        assertFalse(inventory.liveCaSkipReason().isBlank());
        assertFalse(inventory.laptopClickSkipReason().isBlank());
        if (!inventory.discoveryReachable()) {
            assertFalse(inventory.tokenExchangeObserved());
            assertTrue(inventory.livePkceSkipReason().contains("does not start kc.sh")
                    || inventory.livePkceSkipReason().contains("not a live token exchange"));
        }

        Map<String, String> report = G4PkceSpaVerifierHonesty.honestSkipTemplate(inventory);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        G4PkceSpaVerifierHonesty.verify(report);
        assertEquals(G4PkceSpaVerifierHonesty.G4_CLOSE, report.get("g4_close"));
        assertEquals("true", report.get("leftover_rows_kept"));
        assertEquals("true", report.get("discovery_is_not_live_exchange"));
        assertEquals("false", report.get("redo_147"));
        assertEquals("false", report.get("redo_108"));
        assertEquals("false", report.get("p0_m0_redone"));
        assertEquals("closed_unchanged", report.get("g18_status"));
        assertEquals("false", report.get("kc_sh_started"));
    }

    @Test
    void honestyGateAcceptsHonestSkipFixture() {
        G4PkceSpaVerifierHonesty.verify(G4PkceSpaVerifierHonesty.parseReport(fixture("honest-skip.env")));
    }

    @Test
    void honestyGateRejectsFakeLivePass() {
        Map<String, String> fake = new HashMap<>(
                G4PkceSpaVerifierHonesty.parseReport(fixture("fake-live-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G4PkceSpaVerifierHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("live_keycloak_pkce"));
    }

    @Test
    void honestyGateRejectsFakeCaPass() {
        Map<String, String> fake = new HashMap<>(
                G4PkceSpaVerifierHonesty.parseReport(fixture("fake-ca-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G4PkceSpaVerifierHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("live_ca_trust"));
    }

    @Test
    void honestyGateRejectsG4CloseClaim() {
        Map<String, String> fake = new HashMap<>(
                G4PkceSpaVerifierHonesty.parseReport(fixture("fake-g4-close.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G4PkceSpaVerifierHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("g4_close"));
    }

    @Test
    void honestyGateRejectsRedoOfClosedSlices() {
        Map<String, String> fake = new HashMap<>(
                G4PkceSpaVerifierHonesty.parseReport(fixture("honest-skip.env")));
        fake.put("redo_147", "true");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G4PkceSpaVerifierHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("redo_147"));

        fake.put("redo_147", "false");
        fake.put("redo_166", "true");
        IllegalArgumentException redo166 = assertThrows(IllegalArgumentException.class,
                () -> G4PkceSpaVerifierHonesty.verify(fake));
        assertTrue(redo166.getMessage().contains("redo_166"));
    }

    @Test
    void docsReadmeKeepsBothLeftoverRows() throws IOException {
        String text = readExisting(repoFile("docs/README.md"));
        assertTrue(text.contains("[g4-pkce-spa-verifier](./g4-pkce-spa-verifier.md)"));
        assertTrue(text.contains("[g4-pkce-spa-verifier-honesty](./g4-pkce-spa-verifier-honesty.md)"));
        assertFalse(text.toLowerCase(Locale.ROOT).contains("product g4 closed"));
        assertFalse(text.contains("产品 G4 已关闭"));

        String claims = readExisting(repoFile("docs/planning/团队领取任务清单_V1.1.md"));
        assertTrue(claims.contains("g4-pkce-spa-verifier.md"));
        assertTrue(claims.contains("g4-pkce-spa-verifier-honesty.md"));
        assertTrue(claims.contains("不宣称产品 G4 关闭"));
        assertTrue(claims.contains("不要重做 #147"));
        assertFalse(claims.contains("产品 G4 已关闭"));
    }

    private static Path fixture(String name) {
        Path first = Path.of("scripts/g4-pkce-spa-verifier-honesty/fixtures/" + name);
        if (first.toFile().isFile()) {
            return first;
        }
        return Path.of("../scripts/g4-pkce-spa-verifier-honesty/fixtures/" + name);
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
