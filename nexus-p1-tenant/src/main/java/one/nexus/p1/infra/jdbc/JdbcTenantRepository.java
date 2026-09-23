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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import one.nexus.p1.domain.EntitlementPayload;
import one.nexus.p1.domain.EntitlementProjection;
import one.nexus.p1.domain.ProjectionSource;
import one.nexus.p1.domain.Tenant;
import one.nexus.p1.domain.TenantConfig;
import one.nexus.p1.domain.TenantKind;
import one.nexus.p1.domain.TenantStatus;
import one.nexus.p1.port.TableAccessPlan;
import one.nexus.p1.port.TenantAdminInitializer.AdminInitResult;
import one.nexus.p1.port.TenantRepository;

@Repository
@DependsOn("flyway")
public class JdbcTenantRepository implements TenantRepository {

    static final TableAccessPlan TENANT_PLAN = TableAccessPlan.platform("platform.tenant");
    static final TableAccessPlan CONFIG_PLAN = TableAccessPlan.platform("platform.tenant_config");
    static final TableAccessPlan ENTITLEMENT_PLAN = TableAccessPlan.platform("platform.entitlement_projection");
    static final TableAccessPlan ADMIN_PLAN = TableAccessPlan.platform("platform.admin_init_request");

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public JdbcTenantRepository(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public void insert(Tenant tenant, TenantConfig config, EntitlementProjection projection) {
        jdbc.sql("""
                        INSERT INTO platform.tenant (
                          id, code, name, kind, status, legal_name,
                          created_at, created_by, updated_at, updated_by, deleted_at, version)
                        VALUES (
                          :id, :code, :name, :kind, :status, :legal_name,
                          :created_at, :created_by, :updated_at, :updated_by, :deleted_at, :version)
                        """)
                .param("id", tenant.id())
                .param("code", tenant.code())
                .param("name", tenant.name())
                .param("kind", tenant.kind().name())
                .param("status", tenant.status().name())
                .param("legal_name", tenant.legalName())
                .param("created_at", JdbcValues.timestamp(tenant.createdAt()))
                .param("created_by", tenant.createdBy())
                .param("updated_at", JdbcValues.timestamp(tenant.updatedAt()))
                .param("updated_by", tenant.updatedBy())
                .param("deleted_at", JdbcValues.timestamp(tenant.deletedAt()))
                .param("version", tenant.version())
                .update();
        jdbc.sql("""
                        INSERT INTO platform.tenant_config (
                          tenant_id, display_name, locale, timezone, date_format, currency,
                          fiscal_year_start_month, logo_file_id, favicon_file_id, updated_at, updated_by, version)
                        VALUES (
                          :tenant_id, :display_name, :locale, :timezone, :date_format, :currency,
                          :fiscal_year_start_month, :logo_file_id, :favicon_file_id, :updated_at, :updated_by, :version)
                        """)
                .param("tenant_id", config.tenantId())
                .param("display_name", config.displayName())
                .param("locale", config.locale())
                .param("timezone", config.timezone())
                .param("date_format", config.dateFormat())
                .param("currency", config.currency())
                .param("fiscal_year_start_month", config.fiscalYearStartMonth())
                .param("logo_file_id", config.logoFileId())
                .param("favicon_file_id", config.faviconFileId())
                .param("updated_at", JdbcValues.timestamp(config.updatedAt()))
                .param("updated_by", config.updatedBy())
                .param("version", config.version())
                .update();
        jdbc.sql("""
                        INSERT INTO platform.entitlement_projection (
                          tenant_id, source, pack_code, pack_version, sku_code, subscription_id,
                          projected_at, payload, updated_at, version)
                        VALUES (
                          :tenant_id, :source, :pack_code, :pack_version, :sku_code, :subscription_id,
                          :projected_at, CAST(:payload AS jsonb), :updated_at, :version)
                        """)
                .param("tenant_id", projection.tenantId())
                .param("source", projection.source().name())
                .param("pack_code", projection.packCode())
                .param("pack_version", projection.packVersion())
                .param("sku_code", projection.skuCode())
                .param("subscription_id", projection.subscriptionId())
                .param("projected_at", JdbcValues.timestamp(projection.projectedAt()))
                .param("payload", json(projection.payload()))
                .param("updated_at", JdbcValues.timestamp(projection.updatedAt()))
                .param("version", projection.version())
                .update();
    }

    @Override
    public void update(Tenant tenant) {
        int updated = jdbc.sql("""
                        UPDATE platform.tenant
                        SET name = :name, status = :status, legal_name = :legal_name,
                            updated_at = :updated_at, updated_by = :updated_by, version = :version
                        WHERE id = :id AND version = :previous_version AND deleted_at IS NULL
                        """)
                .param("name", tenant.name())
                .param("status", tenant.status().name())
                .param("legal_name", tenant.legalName())
                .param("updated_at", JdbcValues.timestamp(tenant.updatedAt()))
                .param("updated_by", tenant.updatedBy())
                .param("version", tenant.version())
                .param("id", tenant.id())
                .param("previous_version", tenant.version() - 1)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("optimistic lock failed for tenant " + tenant.id());
        }
    }

    @Override
    public void updateConfig(TenantConfig config) {
        int updated = jdbc.sql("""
                        UPDATE platform.tenant_config
                        SET display_name = :display_name, locale = :locale, timezone = :timezone,
                            date_format = :date_format, currency = :currency,
                            fiscal_year_start_month = :fiscal_year_start_month,
                            logo_file_id = :logo_file_id, favicon_file_id = :favicon_file_id,
                            updated_at = :updated_at, updated_by = :updated_by, version = :version
                        WHERE tenant_id = :tenant_id AND version = :previous_version
                        """)
                .param("display_name", config.displayName())
                .param("locale", config.locale())
                .param("timezone", config.timezone())
                .param("date_format", config.dateFormat())
                .param("currency", config.currency())
                .param("fiscal_year_start_month", config.fiscalYearStartMonth())
                .param("logo_file_id", config.logoFileId())
                .param("favicon_file_id", config.faviconFileId())
                .param("updated_at", JdbcValues.timestamp(config.updatedAt()))
                .param("updated_by", config.updatedBy())
                .param("version", config.version())
                .param("tenant_id", config.tenantId())
                .param("previous_version", config.version() - 1)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("optimistic lock failed for tenant config " + config.tenantId());
        }
    }

    @Override
    public Optional<Tenant> findById(UUID id) {
        return jdbc.sql("SELECT * FROM platform.tenant WHERE id = :id AND deleted_at IS NULL")
                .param("id", id)
                .query(this::mapTenant)
                .optional();
    }

    @Override
    public Optional<Tenant> findByCode(String code) {
        return jdbc.sql("SELECT * FROM platform.tenant WHERE code = :code AND deleted_at IS NULL")
                .param("code", code)
                .query(this::mapTenant)
                .optional();
    }

    @Override
    public boolean codeExists(String code) {
        Integer count = jdbc.sql("SELECT count(*) FROM platform.tenant WHERE code = :code")
                .param("code", code)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    @Override
    public List<Tenant> listAll() {
        return jdbc.sql("SELECT * FROM platform.tenant WHERE deleted_at IS NULL ORDER BY created_at")
                .query(this::mapTenant)
                .list();
    }

    @Override
    public Optional<TenantConfig> findConfig(UUID tenantId) {
        return jdbc.sql("SELECT * FROM platform.tenant_config WHERE tenant_id = :tenant_id")
                .param("tenant_id", tenantId)
                .query(this::mapConfig)
                .optional();
    }

    @Override
    public Optional<EntitlementProjection> findProjection(UUID tenantId) {
        return jdbc.sql("SELECT * FROM platform.entitlement_projection WHERE tenant_id = :tenant_id")
                .param("tenant_id", tenantId)
                .query(this::mapProjection)
                .optional();
    }

    @Override
    public void updateProjection(EntitlementProjection projection) {
        int updated = jdbc.sql("""
                        UPDATE platform.entitlement_projection
                        SET source = :source, pack_code = :pack_code, pack_version = :pack_version,
                            sku_code = :sku_code, subscription_id = :subscription_id,
                            projected_at = :projected_at, payload = CAST(:payload AS jsonb),
                            updated_at = :updated_at, version = :version
                        WHERE tenant_id = :tenant_id AND version = :previous_version
                        """)
                .param("source", projection.source().name())
                .param("pack_code", projection.packCode())
                .param("pack_version", projection.packVersion())
                .param("sku_code", projection.skuCode())
                .param("subscription_id", projection.subscriptionId())
                .param("projected_at", JdbcValues.timestamp(projection.projectedAt()))
                .param("payload", json(projection.payload()))
                .param("updated_at", JdbcValues.timestamp(projection.updatedAt()))
                .param("version", projection.version())
                .param("tenant_id", projection.tenantId())
                .param("previous_version", projection.version() - 1)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("optimistic lock failed for entitlement projection " + projection.tenantId());
        }
    }

    @Override
    public void insertAdminInit(
            UUID requestId,
            UUID tenantId,
            String displayName,
            String email,
            AdminInitResult.Status status,
            String message,
            UUID actorId,
            String traceId) {
        jdbc.sql("""
                        INSERT INTO platform.admin_init_request (
                          id, tenant_id, admin_display_name, admin_email, status, message,
                          actor_subject_id, trace_id, created_at)
                        VALUES (
                          :id, :tenant_id, :admin_display_name, :admin_email, :status, :message,
                          :actor_subject_id, :trace_id, :created_at)
                        """)
                .param("id", requestId)
                .param("tenant_id", tenantId)
                .param("admin_display_name", displayName)
                .param("admin_email", email)
                .param("status", status.name())
                .param("message", message)
                .param("actor_subject_id", actorId)
                .param("trace_id", traceId)
                .param("created_at", JdbcValues.timestamp(Instant.now()))
                .update();
    }

    private Tenant mapTenant(ResultSet rs, int rowNum) throws SQLException {
        return new Tenant(
                JdbcValues.uuid(rs, "id"),
                rs.getString("code"),
                rs.getString("name"),
                TenantKind.valueOf(rs.getString("kind")),
                TenantStatus.valueOf(rs.getString("status")),
                rs.getString("legal_name"),
                JdbcValues.instant(rs, "created_at"),
                JdbcValues.uuid(rs, "created_by"),
                JdbcValues.instant(rs, "updated_at"),
                JdbcValues.uuid(rs, "updated_by"),
                JdbcValues.instant(rs, "deleted_at"),
                rs.getLong("version"));
    }

    private TenantConfig mapConfig(ResultSet rs, int rowNum) throws SQLException {
        return new TenantConfig(
                JdbcValues.uuid(rs, "tenant_id"),
                rs.getString("display_name"),
                rs.getString("locale"),
                rs.getString("timezone"),
                rs.getString("date_format"),
                rs.getString("currency"),
                rs.getInt("fiscal_year_start_month"),
                JdbcValues.uuid(rs, "logo_file_id"),
                JdbcValues.uuid(rs, "favicon_file_id"),
                JdbcValues.instant(rs, "updated_at"),
                JdbcValues.uuid(rs, "updated_by"),
                rs.getLong("version"));
    }

    private EntitlementProjection mapProjection(ResultSet rs, int rowNum) throws SQLException {
        EntitlementPayload payload;
        try {
            payload = mapper.readValue(rs.getString("payload"), EntitlementPayload.class);
        } catch (JacksonException ex) {
            throw new IllegalStateException("entitlement payload parse failed", ex);
        }
        return new EntitlementProjection(
                JdbcValues.uuid(rs, "tenant_id"),
                ProjectionSource.valueOf(rs.getString("source")),
                rs.getString("pack_code"),
                rs.getString("pack_version"),
                rs.getString("sku_code"),
                JdbcValues.uuid(rs, "subscription_id"),
                JdbcValues.instant(rs, "projected_at"),
                payload,
                JdbcValues.instant(rs, "updated_at"),
                rs.getLong("version"));
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("json write failed", ex);
        }
    }
}
