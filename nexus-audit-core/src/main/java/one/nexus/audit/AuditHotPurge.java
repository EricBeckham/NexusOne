package one.nexus.audit;

import java.time.Instant;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;

/**
 * Selects a safe 90-day hot prefix and DELETEs those rows. {@link AuditPort}
 * append paths are not used. The hot table is never dropped.
 */
public final class AuditHotPurge implements AuditHotPurgePort {
    @FunctionalInterface
    interface HotRowDeleter {
        void delete(Collection<UUID> eventIds);
    }

    private final AuditPort hot;
    private final AuditArchivePort archive;
    private final AuditObjectStore cold;
    private final HotRowDeleter deleter;

    public AuditHotPurge(
            AuditPort hot, AuditArchivePort archive, AuditObjectStore cold, HotRowDeleter deleter) {
        this.hot = Objects.requireNonNull(hot, "hot");
        this.archive = Objects.requireNonNull(archive, "archive");
        this.cold = cold;
        this.deleter = Objects.requireNonNull(deleter, "deleter");
    }

    public static AuditHotPurge inMemory(
            InMemoryHashChainedAuditPort hot, AuditArchivePort archive, AuditObjectStore cold) {
        return new AuditHotPurge(hot, archive, cold, hot::removeByEventIds);
    }

    public static AuditHotPurge jdbc(
            AuditPort hot, AuditArchivePort archive, AuditObjectStore cold, DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        return new AuditHotPurge(hot, archive, cold, ids -> deleteJdbc(dataSource, ids));
    }

    @Override
    public HotPurgeRunResult purgeEligibleHot(Instant now) {
        Objects.requireNonNull(now, "now");
        List<ChainedAuditEvent> chain = hot.chain();
        Set<UUID> archivedIds = new LinkedHashSet<>();
        for (ChainedAuditEvent linked : archive.findArchived()) {
            archivedIds.add(linked.event().eventId());
        }
        List<ChainedAuditEvent> prefix = AuditHotPurgePolicy.eligiblePrefix(chain, now, archivedIds, cold);
        int aged = AuditHotPurgePolicy.countAged(chain, now);
        Set<UUID> ids = new LinkedHashSet<>();
        for (ChainedAuditEvent linked : prefix) {
            ids.add(linked.event().eventId());
        }
        deleter.delete(ids);
        return new HotPurgeRunResult(
                ids.size(), chain.size() - ids.size(), aged - ids.size(), AuditRetentionPolicy.hotCutoff(now));
    }

    private static void deleteJdbc(DataSource dataSource, Collection<UUID> eventIds) {
        if (eventIds.isEmpty()) {
            return;
        }
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            boolean own = !DataSourceUtils.isConnectionTransactional(connection, dataSource);
            boolean previous = connection.getAutoCommit();
            if (own) {
                connection.setAutoCommit(false);
            }
            try {
                try (PreparedStatement statement =
                        connection.prepareStatement("DELETE FROM platform.audit_record WHERE event_id = ?")) {
                    for (UUID id : eventIds) {
                        statement.setObject(1, id);
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                if (own) {
                    connection.commit();
                }
            } catch (RuntimeException | SQLException ex) {
                if (own) {
                    connection.rollback();
                }
                if (ex instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException("audit hot purge failed", ex);
            } finally {
                if (own) {
                    connection.setAutoCommit(previous);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("audit hot purge failed", ex);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }
}
