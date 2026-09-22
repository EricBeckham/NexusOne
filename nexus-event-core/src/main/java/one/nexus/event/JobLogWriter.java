package one.nexus.event;

import java.util.UUID;

public interface JobLogWriter {
    void write(String channel, UUID sourceId, String jobKey, String tenantId, String traceId, String status, String detail);
}
