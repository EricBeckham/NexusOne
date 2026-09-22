package one.nexus.p3.report;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import one.nexus.common.id.UuidV7;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * In-memory leftover wizard sessions. Not a product Job / Task store.
 * Begin mints a wizard_id; complete must match the same tenant.
 */
@Component
public class ExportWizardSessions {
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    public record Session(String wizardId, String tenantId, String payload, Instant begunAt, boolean completed) {}

    public Session begin(String tenantId, String payload) {
        String id = UuidV7.generate().toString();
        Session session = new Session(
                id,
                tenantId == null ? "" : tenantId,
                payload == null ? "" : payload,
                Instant.now(),
                false);
        sessions.put(id, session);
        return session;
    }

    public Session requireOpen(String wizardId, String tenantId) {
        if (wizardId == null || wizardId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "wizard_id is required");
        }
        Session session = sessions.get(wizardId);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "export wizard session not found");
        }
        String expected = session.tenantId() == null ? "" : session.tenantId();
        String actual = tenantId == null ? "" : tenantId;
        if (!Objects.equals(expected, actual)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "export wizard tenant mismatch");
        }
        if (session.completed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "export wizard already completed");
        }
        return session;
    }

    public Session complete(String wizardId, String tenantId) {
        Session open = requireOpen(wizardId, tenantId);
        Session done = new Session(open.wizardId(), open.tenantId(), open.payload(), open.begunAt(), true);
        sessions.put(wizardId, done);
        return done;
    }
}
