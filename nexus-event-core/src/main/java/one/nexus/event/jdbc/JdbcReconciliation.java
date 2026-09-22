package one.nexus.event.jdbc;

import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import one.nexus.event.DeadLetterStore;
import one.nexus.event.ReconciliationPort;
import one.nexus.event.support.Jdbc;

public final class JdbcReconciliation implements ReconciliationPort {
    private final JdbcOutboxStore outbox;
    private final DataSource dataSource;
    private final JdbcDeadLetterStore deadLetters;
    private final Clock clock;

    public JdbcReconciliation(
            JdbcOutboxStore outbox,
            DataSource dataSource,
            JdbcDeadLetterStore deadLetters,
            Clock clock
    ) {
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.deadLetters = Objects.requireNonNull(deadLetters, "deadLetters");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public int reclaimExpiredLeases() {
        int outboxReclaimed = outbox.reclaimExpiredLeases();
        Instant now = Instant.now(clock);
        int tasks = Jdbc.call(dataSource, connection -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    UPDATE platform.exec_task
                    SET status = 'FAILED', lease_until = NULL, leased_by = NULL, updated_at = ?
                    WHERE status = 'LEASED' AND lease_until IS NOT NULL AND lease_until < ?
                    """)) {
                ps.setTimestamp(1, Jdbc.ts(now));
                ps.setTimestamp(2, Jdbc.ts(now));
                return ps.executeUpdate();
            }
        });
        return outboxReclaimed + tasks;
    }

    @Override
    public int republishStuck(Duration olderThan) {
        return outbox.republishStuck(olderThan);
    }

    @Override
    public boolean replayDead(UUID deadLetterId) {
        var item = deadLetters.find(deadLetterId);
        if (item.isEmpty()) {
            return false;
        }
        DeadLetterStore.DeadLetter letter = item.get();
        if ("OUTBOX".equals(letter.channel())) {
            outbox.requeue(letter.sourceId());
            return true;
        }
        if ("TASK".equals(letter.channel())) {
            Instant now = Instant.now(clock);
            return Jdbc.call(dataSource, connection -> {
                try (PreparedStatement ps = connection.prepareStatement("""
                        UPDATE platform.exec_task
                        SET status = 'PENDING', lease_until = NULL, leased_by = NULL,
                            available_at = ?, updated_at = ?
                        WHERE id = ?
                        """)) {
                    ps.setTimestamp(1, Jdbc.ts(now));
                    ps.setTimestamp(2, Jdbc.ts(now));
                    ps.setObject(3, letter.sourceId());
                    return ps.executeUpdate() > 0;
                }
            });
        }
        return false;
    }
}
