package one.nexus.common.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEventTest {
    @Test
    void requiresTraceAndSortsDetails() {
        UUID id = UUID.fromString("0199a1c0-0000-7000-8000-000000000001");
        AuditEvent event = new AuditEvent(
                id,
                1,
                Instant.parse("2026-09-22T00:00:00Z"),
                AuditCategory.LOGIN,
                "p2.auth.login.accepted",
                id,
                id,
                id,
                false,
                "login",
                "0199a1c0-0000-7000-8000-000000000042",
                "0199a1c0-0000-7000-8000-000000000042",
                null,
                AuditEvent.OUTCOME_ACCEPTED,
                "session",
                "s-1",
                Map.of("b", "2", "a", "1"));
        assertEquals("1", event.details().get("a"));
        assertEquals(java.util.List.of("a", "b"), new java.util.ArrayList<>(event.details().keySet()));
    }

    @Test
    void rejectsMissingTrace() {
        UUID id = UUID.fromString("0199a1c0-0000-7000-8000-000000000001");
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuditEvent(
                        id,
                        1,
                        Instant.parse("2026-09-22T00:00:00Z"),
                        AuditCategory.ADMIN,
                        "p1.tenant.created",
                        id,
                        id,
                        id,
                        true,
                        "create",
                        " ",
                        null,
                        null,
                        AuditEvent.OUTCOME_ACCEPTED,
                        "tenant",
                        id.toString(),
                        Map.of()));
    }
}
