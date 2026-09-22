package one.nexus.p1.infra.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import one.nexus.p1.domain.DomainException;
import one.nexus.p1.domain.ErrorCode;
import one.nexus.p1.domain.Quota;
import one.nexus.p1.domain.QuotaMetric;
import one.nexus.p1.port.QuotaRepository;
import one.nexus.p1.port.TableAccessPlan;

@Repository
@DependsOn("flyway")
public class JdbcQuotaRepository implements QuotaRepository {

    static final TableAccessPlan PLAN = TableAccessPlan.platform("p1.quota");
    static final TableAccessPlan IDEMP_PLAN = TableAccessPlan.platform("p1.quota_idempotency");

    private final JdbcClient jdbc;

    public JdbcQuotaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Quota quota) {
        jdbc.sql("""
                        INSERT INTO p1.quota (
                          tenant_id, metric, limit_value, used_value, warn_percent,
                          over_limit_policy, updated_at, version)
                        VALUES (
                          :tenant_id, :metric, :limit_value, :used_value, :warn_percent,
                          :over_limit_policy, :updated_at, :version)
                        """)
                .param("tenant_id", quota.tenantId())
                .param("metric", quota.metric().name())
                .param("limit_value", quota.limitValue())
                .param("used_value", quota.usedValue())
                .param("warn_percent", quota.warnPercent())
                .param("over_limit_policy", quota.overLimitPolicy())
                .param("updated_at", JdbcValues.timestamp(quota.updatedAt()))
                .param("version", quota.version())
                .update();
    }

    @Override
    public List<Quota> listByTenant(UUID tenantId) {
        return jdbc.sql("SELECT * FROM p1.quota WHERE tenant_id = :tenant_id ORDER BY metric")
                .param("tenant_id", tenantId)
                .query(this::map)
                .list();
    }

    @Override
    public Optional<Quota> find(UUID tenantId, QuotaMetric metric) {
        return jdbc.sql("SELECT * FROM p1.quota WHERE tenant_id = :tenant_id AND metric = :metric")
                .param("tenant_id", tenantId)
                .param("metric", metric.name())
                .query(this::map)
                .optional();
    }

    @Override
    public Quota lock(UUID tenantId, QuotaMetric metric) {
        return jdbc.sql("""
                        SELECT * FROM p1.quota
                        WHERE tenant_id = :tenant_id AND metric = :metric
                        FOR UPDATE
                        """)
                .param("tenant_id", tenantId)
                .param("metric", metric.name())
                .query(this::map)
                .optional()
                .orElseThrow(() -> new DomainException(
                        ErrorCode.QUOTA_INVALID, "quota row missing",
                        java.util.Map.of("tenant_id", tenantId.toString(), "metric", metric.name())));
    }

    @Override
    public void update(Quota quota) {
        int updated = jdbc.sql("""
                        UPDATE p1.quota
                        SET used_value = :used_value, updated_at = :updated_at, version = :version
                        WHERE tenant_id = :tenant_id AND metric = :metric AND version = :previous_version
                        """)
                .param("used_value", quota.usedValue())
                .param("updated_at", JdbcValues.timestamp(quota.updatedAt()))
                .param("version", quota.version())
                .param("tenant_id", quota.tenantId())
                .param("metric", quota.metric().name())
                .param("previous_version", quota.version() - 1)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("optimistic lock failed for quota " + quota.tenantId() + "/" + quota.metric());
        }
    }

    @Override
    public Optional<IdempotencyRecord> findIdempotency(UUID tenantId, QuotaMetric metric, String key) {
        return jdbc.sql("""
                        SELECT * FROM p1.quota_idempotency
                        WHERE tenant_id = :tenant_id AND metric = :metric AND idempotency_key = :key
                        """)
                .param("tenant_id", tenantId)
                .param("metric", metric.name())
                .param("key", key)
                .query(this::mapIdempotency)
                .optional();
    }

    @Override
    public void insertIdempotency(IdempotencyRecord record) {
        jdbc.sql("""
                        INSERT INTO p1.quota_idempotency (
                          tenant_id, metric, idempotency_key, amount, outcome, used_after, limit_value, created_at)
                        VALUES (
                          :tenant_id, :metric, :idempotency_key, :amount, :outcome, :used_after, :limit_value, :created_at)
                        """)
                .param("tenant_id", record.tenantId())
                .param("metric", record.metric().name())
                .param("idempotency_key", record.key())
                .param("amount", record.amount())
                .param("outcome", record.outcome())
                .param("used_after", record.usedAfter())
                .param("limit_value", record.limitValue())
                .param("created_at", JdbcValues.timestamp(record.createdAt()))
                .update();
    }

    private Quota map(ResultSet rs, int rowNum) throws SQLException {
        return new Quota(
                JdbcValues.uuid(rs, "tenant_id"),
                QuotaMetric.valueOf(rs.getString("metric")),
                rs.getInt("limit_value"),
                rs.getInt("used_value"),
                rs.getInt("warn_percent"),
                rs.getString("over_limit_policy"),
                JdbcValues.instant(rs, "updated_at"),
                rs.getLong("version"));
    }

    private IdempotencyRecord mapIdempotency(ResultSet rs, int rowNum) throws SQLException {
        return new IdempotencyRecord(
                JdbcValues.uuid(rs, "tenant_id"),
                QuotaMetric.valueOf(rs.getString("metric")),
                rs.getString("idempotency_key"),
                rs.getInt("amount"),
                rs.getString("outcome"),
                (Integer) rs.getObject("used_after"),
                (Integer) rs.getObject("limit_value"),
                JdbcValues.instant(rs, "created_at"));
    }
}
