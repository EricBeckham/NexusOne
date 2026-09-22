package one.nexus.p3.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ExportWizardSessionsTest {
    @Test
    void beginThenCompleteMatchesTenant() {
        ExportWizardSessions sessions = new ExportWizardSessions();
        ExportWizardSessions.Session begun = sessions.begin("tenant-a", "{\"ledger\":\"leave\"}");
        assertFalse(begun.completed());
        ExportWizardSessions.Session done = sessions.complete(begun.wizardId(), "tenant-a");
        assertTrue(done.completed());
        assertEquals("{\"ledger\":\"leave\"}", done.payload());
        ResponseStatusException replay = assertThrows(
                ResponseStatusException.class, () -> sessions.complete(begun.wizardId(), "tenant-a"));
        assertEquals(HttpStatus.CONFLICT, replay.getStatusCode());
    }

    @Test
    void unknownOrForeignTenantIsRejected() {
        ExportWizardSessions sessions = new ExportWizardSessions();
        ExportWizardSessions.Session begun = sessions.begin("tenant-a", "{}");
        ResponseStatusException missing = assertThrows(
                ResponseStatusException.class, () -> sessions.requireOpen("missing", "tenant-a"));
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
        ResponseStatusException foreign = assertThrows(
                ResponseStatusException.class, () -> sessions.requireOpen(begun.wizardId(), "tenant-b"));
        assertEquals(HttpStatus.FORBIDDEN, foreign.getStatusCode());
        ResponseStatusException blank = assertThrows(
                ResponseStatusException.class, () -> sessions.requireOpen(" ", "tenant-a"));
        assertEquals(HttpStatus.BAD_REQUEST, blank.getStatusCode());
    }
}
