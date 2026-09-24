package one.nexus.p3.studio.form;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublishCenterStatusTest {
    @Test
    void mapsKnownTokensAndKeepsUnknownOnes() {
        assertEquals("Draft", PublishCenterStatus.map("DRAFT"));
        assertEquals("Ready", PublishCenterStatus.map("VALIDATED"));
        assertEquals("Publishing", PublishCenterStatus.map("PUBLISHING"));
        assertEquals("Running", PublishCenterStatus.map("RUNNING"));
        assertEquals("Failed", PublishCenterStatus.map("FAILED"));
        assertEquals("RolledBack", PublishCenterStatus.map("ROLLED_BACK"));
        assertEquals("SUPERSEDED", PublishCenterStatus.map("SUPERSEDED"));
    }

    @Test
    void prefersRollbackThenRunningVersionOverTheDraftLabel() {
        FormRecord draft = form(null, FormStatus.DRAFT);
        assertEquals("Draft", PublishCenterStatus.of(draft, null));
        FormRecord validated = form(null, FormStatus.VALIDATED);
        assertEquals("Ready", PublishCenterStatus.of(validated, log("VALIDATED", "ACCEPTED")));
        FormRecord running = form(1, FormStatus.DRAFT);
        assertEquals("Running", PublishCenterStatus.of(running, log("RUNNING", "ACCEPTED")));
        assertEquals("RolledBack", PublishCenterStatus.of(draft, log("FAILED", "ROLLED_BACK")));
        assertTrue(one.nexus.p3.path.BusinessTypeCatalog.findByFormCode("leave_form").isPresent());
        assertTrue(one.nexus.p3.path.BusinessTypeCatalog.findByFormCode("center_form").isEmpty());
    }

    private static FormRecord form(Integer running, FormStatus status) {
        Instant now = Instant.parse("2026-09-24T00:00:00Z");
        return new FormRecord(
                UUID.randomUUID(), UUID.randomUUID(), "demo", "leave_form", "请假单", null,
                status, running, java.util.List.of(), now, "user", now, "user");
    }

    private static PublishLog log(String toStatus, String outcome) {
        return new PublishLog(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                "DRAFT", toStatus, outcome, "msg", Map.of(), "trace", Instant.parse("2026-09-24T00:00:01Z"));
    }
}
