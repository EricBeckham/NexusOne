package one.nexus.p1.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import one.nexus.p1.domain.AuditEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * G4 leftover honesty beyond #156: staging-like query/export stays PASS;
 * live {@code :5432} is honest SKIP when unavailable. Does not switch GET
 * off staging. Does not redo #147/#152/#156/#166.
 */
class G4P1StagingAuditEventsHonestyTest {

    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID OTHER = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID ACTOR = UUID.fromString("01900000-0000-7000-8000-0000000000ac");

    @TempDir
    Path tmp;

    @Test
    void stagingLikeQueryAndExportStayPass() throws Exception {
        InMemoryAuditRepository staging = new InMemoryAuditRepository();
        staging.insert(event(TENANT, AuditEvent.TENANT_CREATED, Instant.parse("2026-09-22T18:00:00Z"), "trace-create"));
        staging.insert(event(TENANT, AuditEvent.TENANT_ENABLED, Instant.parse("2026-09-22T18:01:00Z"), "trace-enable"));
        staging.insert(event(OTHER, AuditEvent.TENANT_CREATED, Instant.parse("2026-09-22T18:02:00Z"), "trace-other"));

        P1StagingAuditEventsExporter exporter = new P1StagingAuditEventsExporter(staging);
        List<AuditEvent> all = exporter.query(TENANT, null, 50);
        assertThat(all).extracting(AuditEvent::eventType)
                .containsExactly(AuditEvent.TENANT_ENABLED, AuditEvent.TENANT_CREATED);

        Map<String, Object> bundle = exporter.exportBundle(TENANT, null, 50);
        assertThat(bundle.get("source_table")).isEqualTo("platform.audit_event");
        assertThat(bundle.get("switched_off_staging")).isEqualTo(false);
        assertThat(bundle.get("product_g4_closed")).isEqualTo(false);
        assertThat(bundle.get("http_export_wizard")).isEqualTo(false);
        assertThat(bundle.get("count")).isEqualTo(2);

        Path json = exporter.writeJson(tmp.resolve("staging-audit-events.json"), TENANT, AuditEvent.TENANT_CREATED, 50);
        JsonNode parsed = new ObjectMapper().readTree(Files.readString(json));
        assertThat(parsed.path("count").asInt()).isEqualTo(1);
        assertThat(parsed.path("switched_off_staging").asBoolean()).isFalse();
        assertThat(parsed.path("product_g4_closed").asBoolean()).isFalse();
    }

    @Test
    void productGetStillReadsStagingRepository() throws Exception {
        String source = Files.readString(G4P1StagingAuditEventsHonesty.controllerSource());
        assertTrue(G4P1StagingAuditEventsHonesty.productGetStillReadsStaging(source));
        assertThat(source).doesNotContain("one.nexus.audit.AuditPort");
        assertThat(source).doesNotContain("findByTraceId");
    }

    @Test
    void liveProbeCannotClaimLiveWalks() {
        G4P1StagingAuditEventsHonesty.Inventory inventory = G4P1StagingAuditEventsHonesty.probeLive();
        assertEquals("SKIP", inventory.liveStaging5432());
        assertFalse(inventory.skipReason().isBlank());
        if (!inventory.livePresent()) {
            assertTrue(inventory.skipReason().contains("SKIP")
                    || inventory.skipReason().contains("unset")
                    || inventory.skipReason().contains("not reachable")
                    || inventory.skipReason().contains("not observed"));
        }

        Map<String, String> report = G4P1StagingAuditEventsHonesty.honestSkipTemplate(inventory);
        report.put("timestamp_utc", "2026-09-22T00:00:00Z");
        G4P1StagingAuditEventsHonesty.verify(report);
        assertEquals(G4P1StagingAuditEventsHonesty.G4_CLOSE, report.get("g4_close"));
        assertEquals("PASS", report.get("staging_like_path"));
        assertEquals("true", report.get("get_still_on_staging"));
        assertEquals("false", report.get("switched_off_staging"));
        assertEquals("false", report.get("redo_156"));
        assertEquals("false", report.get("redo_152"));
        assertEquals("false", report.get("p0_m0_redone"));
        assertEquals("closed_unchanged", report.get("g18_status"));
    }

    @Test
    void honestyGateAcceptsHonestSkipFixture() {
        G4P1StagingAuditEventsHonesty.verify(G4P1StagingAuditEventsHonesty.parseReport(fixture("honest-skip.env")));
    }

    @Test
    void honestyGateRejectsFakeLivePass() {
        Map<String, String> fake = new HashMap<>(
                G4P1StagingAuditEventsHonesty.parseReport(fixture("fake-live-pass.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G4P1StagingAuditEventsHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("live_staging_5432"));
    }

    @Test
    void honestyGateRejectsG4CloseClaim() {
        Map<String, String> fake = new HashMap<>(
                G4P1StagingAuditEventsHonesty.parseReport(fixture("fake-g4-close.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G4P1StagingAuditEventsHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("g4_close"));
    }

    @Test
    void honestyGateRejectsSwitchingGetOffStaging() {
        Map<String, String> fake = new HashMap<>(
                G4P1StagingAuditEventsHonesty.parseReport(fixture("fake-switched-off-staging.env")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> G4P1StagingAuditEventsHonesty.verify(fake));
        assertTrue(ex.getMessage().contains("switched_off_staging"));
    }

    @Test
    void honestyGateRejectsRedoOfClosedSlices() {
        Map<String, String> fake = new HashMap<>(
                G4P1StagingAuditEventsHonesty.parseReport(fixture("honest-skip.env")));
        fake.put("redo_156", "true");
        IllegalArgumentException redo156 = assertThrows(IllegalArgumentException.class,
                () -> G4P1StagingAuditEventsHonesty.verify(fake));
        assertTrue(redo156.getMessage().contains("redo_156"));

        fake.put("redo_156", "false");
        fake.put("redo_152", "true");
        IllegalArgumentException redo152 = assertThrows(IllegalArgumentException.class,
                () -> G4P1StagingAuditEventsHonesty.verify(fake));
        assertTrue(redo152.getMessage().contains("redo_152"));

        fake.put("redo_152", "false");
        fake.put("redo_166", "true");
        IllegalArgumentException redo166 = assertThrows(IllegalArgumentException.class,
                () -> G4P1StagingAuditEventsHonesty.verify(fake));
        assertTrue(redo166.getMessage().contains("redo_166"));

        fake.put("redo_166", "false");
        fake.put("redo_147", "true");
        IllegalArgumentException redo147 = assertThrows(IllegalArgumentException.class,
                () -> G4P1StagingAuditEventsHonesty.verify(fake));
        assertTrue(redo147.getMessage().contains("redo_147"));
    }

    @Test
    void docsReadmeKeepsAllSiblingLeftoverRows() throws IOException {
        String text = readExisting(repoFile("docs/README.md"));
        assertTrue(text.contains("[g4-p1-staging-audit-events](./g4-p1-staging-audit-events.md)"));
        assertTrue(text.contains("[g4-p1-staging-audit-events-honesty](./g4-p1-staging-audit-events-honesty.md)"));
        assertTrue(text.contains("[g4-export-wizard](./g4-export-wizard.md)"));
        assertTrue(text.contains("[g4-export-wizard-deny](./g4-export-wizard-deny.md)"));
        assertTrue(text.contains("[g4-pkce-spa-verifier](./g4-pkce-spa-verifier.md)"));
        assertTrue(text.contains("[g4-pkce-spa-verifier-honesty](./g4-pkce-spa-verifier-honesty.md)"));
        assertTrue(text.contains("[g10-identity-drift-honesty](./g10-identity-drift-honesty.md)"));
        assertTrue(text.contains("[g15-native-bridge-honesty](./g15-native-bridge-honesty.md)"));
        assertTrue(text.contains("[s0-secret-getenv-snapshot-honesty](./s0-secret-getenv-snapshot-honesty.md)"));
        assertTrue(text.contains("[g5-browser-failed-publish](./g5-browser-failed-publish.md)"));
        assertTrue(text.contains("[g1-fieldpolicy-mask-second-auth](./g1-fieldpolicy-mask-second-auth.md)"));
        assertTrue(text.contains("[g1-fieldpolicy-mask-second-auth-honesty](./g1-fieldpolicy-mask-second-auth-honesty.md)"));
        assertTrue(text.contains("[g4-hot-purge-90d](./g4-hot-purge-90d.md)"));
        assertTrue(text.contains("[g4-hot-purge-90d-honesty](./g4-hot-purge-90d-honesty.md)"));
        assertTrue(text.contains("[g17-separation-of-duties](./g17-separation-of-duties.md)"));
        assertTrue(text.contains("[g17-separation-honesty](./g17-separation-honesty.md)"));
        assertTrue(text.contains("[s0s1-live-redis-minio-isolation-honesty](./s0s1-live-redis-minio-isolation-honesty.md)"));
        assertTrue(text.contains("[g6-recovery-port-dry-run-honesty](./g6-recovery-port-dry-run-honesty.md)"));
        assertFalse(text.toLowerCase(Locale.ROOT).contains("product g4 closed"));
        assertFalse(text.contains("产品 G4 已关闭"));

        String claims = readExisting(repoFile("docs/planning/团队领取任务清单_V1.1.md"));
        assertTrue(claims.contains("g4-p1-staging-audit-events.md"));
        assertTrue(claims.contains("g4-p1-staging-audit-events-honesty.md"));
        assertTrue(claims.contains("不宣称产品 G4 关闭"));
        assertTrue(claims.contains("不要重做 #156"));
        assertFalse(claims.contains("产品 G4 已关闭"));
    }

    private static AuditEvent event(UUID tenant, String type, Instant occurredAt, String trace) {
        return new AuditEvent(
                UUID.randomUUID(),
                type,
                AuditEvent.VERSION,
                occurredAt,
                tenant,
                ACTOR,
                tenant,
                true,
                "g4-p1-staging-honesty",
                trace,
                trace,
                null,
                AuditEvent.OUTCOME_ACCEPTED,
                "tenant",
                tenant.toString(),
                Map.of("harness", "staging-honesty"));
    }

    private static Path fixture(String name) {
        Path first = Path.of("scripts/g4-p1-staging-audit-events-honesty/fixtures/" + name);
        if (first.toFile().isFile()) {
            return first;
        }
        return Path.of("../scripts/g4-p1-staging-audit-events-honesty/fixtures/" + name);
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
