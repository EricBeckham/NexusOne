package one.nexus.event;

import java.util.List;
import java.util.UUID;

public interface DeadLetterStore {
    void record(String channel, UUID sourceId, String tenantId, String traceId,
                String payloadType, String payload, String lastError, int attempt);

    List<DeadLetter> list(String channel, int limit);

    record DeadLetter(
            UUID id,
            String channel,
            UUID sourceId,
            String tenantId,
            String traceId,
            String payloadType,
            String payload,
            String lastError,
            int attempt
    ) {}
}
