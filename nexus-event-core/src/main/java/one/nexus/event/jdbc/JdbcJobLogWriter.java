package one.nexus.event.jdbc;

import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import one.nexus.common.id.UuidV7;
import one.nexus.event.JobLogWriter;
import one.nexus.event.support.Jdbc;

public final class JdbcJobLogWriter implements JobLogWriter {
    private final DataSource dataSource;
    private final Clock clock;

    public JdbcJobLogWriter(DataSource dataSource, Clock clock) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public void write(String channel, UUID sourceId, String jobKey, String tenantId, String traceId, String status, String detail) {
        Instant now = Instant.now(clock);
        Jdbc.run(dataSource, connection -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO platform.exec_job_log (
                        id, channel, source_id, job_key, tenant_id, trace_id, status, detail, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                ps.setObject(1, UuidV7.generate());
                ps.setString(2, channel);
                ps.setObject(3, sourceId);
                ps.setString(4, jobKey);
                ps.setString(5, tenantId);
                ps.setString(6, traceId);
                ps.setString(7, status);
                ps.setString(8, detail);
                ps.setTimestamp(9, Jdbc.ts(now));
                ps.executeUpdate();
            }
            return null;
        });
    }
}
