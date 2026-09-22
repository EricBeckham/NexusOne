package one.nexus.p1.audit;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.p1.domain.AuditEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class P1StagingAuditEventsHonestyTest {

    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID OTHER = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID ACTOR = UUID.fromString("01900000-0000-7000-8000-0000000000ac");

    @TempDir
    Path tmp;

    @Test
    void queryAndExportStayOnStagingShape() throws Exception {
        InMemoryAuditRepository staging = new InMemoryAuditRepository();
        staging.insert(event(TENANT, AuditEvent.TENANT_CREATED, Instant.parse("2026-09-22T18:00:00Z"), "trace-create"));
        staging.insert(event(TENANT, AuditEvent.TENANT_ENABLED, Instant.parse("2026-09-22T18:01:00Z"), "trace-enable"));
        staging.insert(event(OTHER, AuditEvent.TENANT_CREATED, Instant.parse("2026-09-22T18:02:00Z"), "trace-other"));

        P1StagingAuditEventsExporter exporter = new P1StagingAuditEventsExporter(staging);
        List<AuditEvent> all = exporter.query(TENANT, null, 50);
        assertThat(all).extracting(AuditEvent::eventType)
                .containsExactly(AuditEvent.TENANT_ENABLED, AuditEvent.TENANT_CREATED);

        List<AuditEvent> created = exporter.query(TENANT, AuditEvent.TENANT_CREATED, 50);
        assertThat(created).hasSize(1);
        assertThat(created.getFirst().traceId()).isEqualTo("trace-create");

        Map<String, Object> bundle = exporter.exportBundle(TENANT, null, 50);
        assertThat(bundle.get("leftover")).isEqualTo(P1StagingAuditEventsExporter.LEFTOVER);
        assertThat(bundle.get("source_table")).isEqualTo("platform.audit_event");
        assertThat(bundle.get("query_path")).isEqualTo("GET /api/v1/p1/tenants/{id}/audit-events");
        assertThat(bundle.get("switched_off_staging")).isEqualTo(false);
        assertThat(bundle.get("product_g4_closed")).isEqualTo(false);
        assertThat(bundle.get("http_export_wizard")).isEqualTo(false);
        assertThat(bundle.get("count")).isEqualTo(2);

        Path json = exporter.writeJson(tmp.resolve("staging-audit-events.json"), TENANT, AuditEvent.TENANT_CREATED, 50);
        JsonNode parsed = new ObjectMapper().readTree(Files.readString(json));
        assertThat(parsed.path("count").asInt()).isEqualTo(1);
        assertThat(parsed.path("events").get(0).path("event_type").asText())
                .isEqualTo(AuditEvent.TENANT_CREATED);
        assertThat(parsed.path("events").get(0).path("outcome").asText()).isEqualTo(AuditEvent.OUTCOME_ACCEPTED);
    }

    @Test
    void productGetStillReadsStagingRepository() throws Exception {
        Path controller = Path.of("src/main/java/one/nexus/p1/api/TenantController.java");
        if (!Files.isRegularFile(controller)) {
            controller = Path.of("nexus-p1-tenant/src/main/java/one/nexus/p1/api/TenantController.java");
        }
        String source = Files.readString(controller);
        assertThat(source).contains("@GetMapping(\"/tenants/{id}/audit-events\")");
        assertThat(source).contains("audit.listByTenant(id, capped)");
        assertThat(source).contains("audit.listByType(id, eventType, capped)");
        assertThat(source).contains("import one.nexus.p1.port.AuditRepository;");
        assertThat(source).doesNotContain("one.nexus.audit.AuditPort");
        assertThat(source).doesNotContain("findByTraceId");
        assertThat(source).contains("Temporary read of P1-local audit staging rows");
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
                "g4-p1-staging-harness",
                trace,
                trace,
                null,
                AuditEvent.OUTCOME_ACCEPTED,
                "tenant",
                tenant.toString(),
                Map.of("harness", "staging"));
    }
}
