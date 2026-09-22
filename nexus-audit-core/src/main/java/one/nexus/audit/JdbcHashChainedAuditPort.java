package one.nexus.audit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import one.nexus.common.audit.AuditCategory;
import one.nexus.common.audit.AuditEvent;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.crypto.CryptoPort;
import one.nexus.crypto.CryptoSuite;
import org.springframework.jdbc.datasource.DataSourceUtils;

/**
 * PostgreSQL append-only store for {@code platform.audit_record}. Mutations other
 * than INSERT are not issued. Full business-path verification is still deferred.
 *
 * <p>{@link #append(AuditEvent)} uses {@link DataSourceUtils} so the chain-head
 * {@code SELECT ... FOR UPDATE}, the record INSERT, and the
 * {@code audit_chain_meta} head update join an ambient Spring transaction on the
 * shared DataSource. There is no private commit on that path: business rollback
 * rolls the chain writes back with the business row.
 *
 * <p>Trade-off: the single-row chain lock is held until the ambient transaction
 * ends, so concurrent {@code append} callers serialize on
 * {@code platform.audit_chain_meta}. That keeps hash-chain indexes gapless and
 * append-only. Splitting the lock into a shorter private transaction would
 * re-introduce chain/business divergence.
 *
 * <p>{@link #appendNewTransaction(AuditEvent)} still commits on a private
 * connection so guard-reject records can survive a business rollback. Do not
 * call it from a thread that already holds the chain lock via {@code append}
 * (two connections waiting on the same {@code FOR UPDATE} deadlock).
 */
public final class JdbcHashChainedAuditPort implements AuditPort {
    private static final String INSERT =
            """
            INSERT INTO platform.audit_record (
              chain_index, event_id, event_version, occurred_at, category, event_type,
              tenant_id, actor_subject_id, actor_tenant_id, platform_context, reason,
              trace_id, correlation_id, causation_id, outcome, resource_type, resource_id,
              details, previous_hash, event_hash, cipher_suite, digest_key_version)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    private final DataSource dataSource;
    private final CryptoPort crypto;
    private final AuditHashChain chain;

    public JdbcHashChainedAuditPort(DataSource dataSource, CryptoPort crypto) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.crypto = Objects.requireNonNull(crypto, "crypto");
        this.chain = new AuditHashChain(crypto);
    }

    @Override
    public ChainedAuditEvent append(AuditEvent event) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            boolean own = !DataSourceUtils.isConnectionTransactional(connection, dataSource);
            return write(connection, event, own);
        } catch (SQLException ex) {
            throw new IllegalStateException("audit append failed", ex);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    @Override
    public ChainedAuditEvent appendNewTransaction(AuditEvent event) {
        try (Connection connection = dataSource.getConnection()) {
            return write(connection, event, true);
        } catch (SQLException ex) {
            throw new IllegalStateException("audit append failed", ex);
        }
    }

    private ChainedAuditEvent write(Connection connection, AuditEvent event, boolean ownTransaction)
            throws SQLException {
        boolean previous = connection.getAutoCommit();
        if (ownTransaction) {
            connection.setAutoCommit(false);
        }
        try {
            ChainHead head = lockHead(connection);
            ChainedAuditEvent linked = chain.link(head.nextIndex(), head.lastHash(), event);
            insert(connection, linked);
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE platform.audit_chain_meta SET next_index = ?, last_hash = ? WHERE id = 1")) {
                update.setLong(1, linked.chainIndex() + 1);
                update.setBytes(2, linked.eventHash());
                update.executeUpdate();
            }
            if (ownTransaction) {
                connection.commit();
            }
            return linked;
        } catch (RuntimeException | SQLException ex) {
            if (ownTransaction) {
                connection.rollback();
            }
            throw ex;
        } finally {
            if (ownTransaction) {
                connection.setAutoCommit(previous);
            }
        }
    }

    @Override
    public List<ChainedAuditEvent> findByTraceId(String traceId) {
        return query("SELECT * FROM platform.audit_record WHERE trace_id = ? ORDER BY chain_index", traceId);
    }

    @Override
    public List<ChainedAuditEvent> findByCorrelationId(String correlationId) {
        return query(
                "SELECT * FROM platform.audit_record WHERE correlation_id = ? ORDER BY chain_index", correlationId);
    }

    @Override
    public Optional<ChainedAuditEvent> findByEventId(UUID eventId) {
        List<ChainedAuditEvent> found =
                query("SELECT * FROM platform.audit_record WHERE event_id = ?", eventId);
        return found.stream().findFirst();
    }

    @Override
    public List<ChainedAuditEvent> chain() {
        return query("SELECT * FROM platform.audit_record ORDER BY chain_index");
    }

    @Override
    public HashChainVerification verifyChain() {
        return chain.verify(chain());
    }

    private ChainHead lockHead(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT next_index, last_hash, cipher_suite FROM platform.audit_chain_meta WHERE id = 1 FOR UPDATE")) {
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("audit_chain_meta is missing");
                }
                CryptoSuite stored = CryptoSuite.valueOf(rs.getString("cipher_suite"));
                long nextIndex = rs.getLong("next_index");
                if (nextIndex == 0 && stored != crypto.suite()) {
                    try (PreparedStatement adopt = connection.prepareStatement(
                            "UPDATE platform.audit_chain_meta SET cipher_suite = ? WHERE id = 1")) {
                        adopt.setString(1, crypto.suite().name());
                        adopt.executeUpdate();
                    }
                    stored = crypto.suite();
                }
                if (stored != crypto.suite()) {
                    throw new NexusException(
                            ErrorCode.CRYPTO_SUITE_MISMATCH,
                            500,
                            "audit chain suite " + stored + " does not match CryptoPort " + crypto.suite());
                }
                return new ChainHead(nextIndex, rs.getBytes("last_hash"));
            }
        }
    }

    private void insert(Connection connection, ChainedAuditEvent linked) throws SQLException {
        AuditEvent event = linked.event();
        try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
            int i = 1;
            statement.setLong(i++, linked.chainIndex());
            statement.setObject(i++, event.eventId());
            statement.setInt(i++, event.eventVersion());
            statement.setObject(i++, event.occurredAt().atOffset(ZoneOffset.UTC));
            statement.setString(i++, event.category().name());
            statement.setString(i++, event.eventType());
            statement.setObject(i++, event.tenantId());
            statement.setObject(i++, event.actorSubjectId());
            statement.setObject(i++, event.actorTenantId());
            statement.setBoolean(i++, event.platformContext());
            statement.setString(i++, event.reason());
            statement.setString(i++, event.traceId());
            statement.setString(i++, event.correlationId());
            statement.setString(i++, event.causationId());
            statement.setString(i++, event.outcome());
            statement.setString(i++, event.resourceType());
            statement.setString(i++, event.resourceId());
            statement.setObject(i++, CanonicalAudit.detailsJson(event.details()), java.sql.Types.OTHER);
            statement.setBytes(i++, linked.previousHash());
            statement.setBytes(i++, linked.eventHash());
            statement.setString(i++, linked.suite().name());
            statement.setString(i++, linked.digestKeyVersion());
            statement.executeUpdate();
        }
    }

    private List<ChainedAuditEvent> query(String sql, Object... args) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<ChainedAuditEvent> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(map(rs));
                }
                return List.copyOf(out);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("audit query failed", ex);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private static ChainedAuditEvent map(ResultSet rs) throws SQLException {
        OffsetDateTime occurred = rs.getObject("occurred_at", OffsetDateTime.class);
        AuditEvent event = new AuditEvent(
                rs.getObject("event_id", UUID.class),
                rs.getInt("event_version"),
                occurred.toInstant(),
                AuditCategory.valueOf(rs.getString("category")),
                rs.getString("event_type"),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("actor_subject_id", UUID.class),
                rs.getObject("actor_tenant_id", UUID.class),
                rs.getBoolean("platform_context"),
                rs.getString("reason"),
                rs.getString("trace_id"),
                rs.getString("correlation_id"),
                rs.getString("causation_id"),
                rs.getString("outcome"),
                rs.getString("resource_type"),
                rs.getString("resource_id"),
                CanonicalAudit.parseDetailsJson(rs.getString("details")));
        return new ChainedAuditEvent(
                rs.getLong("chain_index"),
                event,
                rs.getBytes("previous_hash"),
                rs.getBytes("event_hash"),
                CryptoSuite.valueOf(rs.getString("cipher_suite")),
                rs.getString("digest_key_version"));
    }

    private record ChainHead(long nextIndex, byte[] lastHash) {}
}
