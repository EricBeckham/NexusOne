package one.nexus.notification;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import one.nexus.common.id.UuidV7;
import one.nexus.event.OutboxAppendCommand;
import one.nexus.event.OutboxPort;

/**
 * Test / no-DataSource fallback. Records {@link OutboxAppendCommand}s without
 * writing {@code platform.exec_outbox}. The monolith uses M0-09 {@code JdbcOutboxStore}.
 */
public final class RecordingOutboxPort implements OutboxPort {
    private final List<OutboxAppendCommand> recorded = new CopyOnWriteArrayList<>();

    @Override
    public UUID append(OutboxAppendCommand command) {
        recorded.add(command);
        return UuidV7.generate();
    }

    public List<OutboxAppendCommand> recorded() {
        return List.copyOf(recorded);
    }
}
