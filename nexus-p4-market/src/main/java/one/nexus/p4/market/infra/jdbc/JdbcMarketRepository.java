package one.nexus.p4.market.infra.jdbc;

import static one.nexus.common.time.Utc.sqlTimestamp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.RenderedSql;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p4.market.MarketIsolation;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Qualifier;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import one.nexus.p4.market.MarketRepository;
import one.nexus.p4.market.MarketModels.*;

@Repository
@DependsOn("flyway")
public class JdbcMarketRepository implements MarketRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final MarketIsolation isolation;

    public JdbcMarketRepository(
            JdbcClient jdbc,
            ObjectMapper json,
            @Qualifier("marketIsolation") MarketIsolation marketIsolation) {
        this.jdbc = jdbc;
        this.json = json;
        this.isolation = marketIsolation;
    }

    @Override public void saveApp(AppPackage a) {
        jdbc.sql("""
                INSERT INTO market.app_package(id,code,name,summary,created_at) VALUES(:id,:code,:name,:summary,:at)
                ON CONFLICT(id) DO UPDATE SET code=:code,name=:name,summary=:summary
                """)
                .param("id",a.id()).param("code",a.code()).param("name",a.name()).param("summary",a.summary())
                .param("at",sqlTimestamp(a.createdAt())).update();
    }
    @Override public void saveVersion(AppVersion v) {
        jdbc.sql("""
                INSERT INTO market.app_version(id,app_id,version,status,release_notes,created_at,published_at)
                VALUES(:id,:app,:version,:status,:notes,:created,:published) ON CONFLICT(id) DO UPDATE SET
                status=:status,published_at=:published,release_notes=:notes
                """)
                .param("id",v.id()).param("app",v.appId()).param("version",v.version()).param("status",v.status().name())
                .param("notes",v.releaseNotes()).param("created",sqlTimestamp(v.createdAt()))
                .param("published",sqlTimestamp(v.publishedAt())).update();
    }
    @Override public void saveSku(Sku s) {
        jdbc.sql("""
                INSERT INTO market.sku(code,app_id,name,entitlements) VALUES(:code,:app,:name,CAST(:items AS jsonb))
                ON CONFLICT(code) DO UPDATE SET app_id=:app,name=:name,entitlements=CAST(:items AS jsonb)
                """)
                .param("code",s.code()).param("app",s.appId()).param("name",s.name()).param("items",write(s.entitlements())).update();
    }
    @Override public void saveSubscription(Subscription s) {
        assertCurrentTenant(s.tenantId());
        jdbc.sql("""
                INSERT INTO market.subscription(id,tenant_id,sku_code,status,started_at,ended_at)
                VALUES(:id,:tenant,:sku,:status,:started,:ended) ON CONFLICT(id) DO UPDATE SET status=:status,ended_at=:ended
                """)
                .param("id",s.id()).param("tenant",s.tenantId()).param("sku",s.skuCode()).param("status",s.status().name())
                .param("started",sqlTimestamp(s.startedAt())).param("ended",sqlTimestamp(s.endedAt())).update();
    }
    @Override public void saveInstallation(Installation i) {
        assertCurrentTenant(i.tenantId());
        jdbc.sql("""
                INSERT INTO market.installation(id,tenant_id,app_id,version_id,status,installed_at,updated_at)
                VALUES(:id,:tenant,:app,:version,:status,:installed,:updated) ON CONFLICT(id) DO UPDATE SET
                version_id=:version,status=:status,updated_at=:updated
                """)
                .param("id",i.id()).param("tenant",i.tenantId()).param("app",i.appId()).param("version",i.versionId())
                .param("status",i.status().name()).param("installed",sqlTimestamp(i.installedAt()))
                .param("updated",sqlTimestamp(i.updatedAt())).update();
    }
    @Override public List<AppPackage> apps() {
        return jdbc.sql("SELECT * FROM market.app_package ORDER BY created_at DESC").query(this::mapApp).list();
    }
    @Override public Optional<AppPackage> app(UUID id) { return jdbc.sql("SELECT * FROM market.app_package WHERE id=:id").param("id",id).query(this::mapApp).optional(); }
    @Override public List<AppVersion> versions(UUID appId) {
        return jdbc.sql("SELECT * FROM market.app_version WHERE app_id=:id ORDER BY created_at DESC")
                .param("id", appId).query(this::mapVersion).list();
    }
    @Override public Optional<AppVersion> version(UUID id) { return jdbc.sql("SELECT * FROM market.app_version WHERE id=:id").param("id",id).query(this::mapVersion).optional(); }
    @Override public List<Sku> skus() { return jdbc.sql("SELECT * FROM market.sku ORDER BY code").query(this::mapSku).list(); }
    @Override public Optional<Sku> sku(String code) { return jdbc.sql("SELECT * FROM market.sku WHERE code=:code").param("code",code).query(this::mapSku).optional(); }
    @Override public List<Installation> installations() {
        return tenantRows(
                "market.installation",
                List.of("id", "tenant_id", "app_id", "version_id", "status", "installed_at", "updated_at"),
                Map.of(),
                this::mapInstallation);
    }
    @Override public Optional<Installation> installationForCurrentTenant(UUID id) {
        return tenantRow(
                "market.installation",
                List.of("id", "tenant_id", "app_id", "version_id", "status", "installed_at", "updated_at"),
                Map.of("id", id),
                this::mapInstallation);
    }
    @Override public List<Subscription> subscriptions() {
        return tenantRows(
                "market.subscription",
                List.of("id", "tenant_id", "sku_code", "status", "started_at", "ended_at"),
                Map.of(),
                this::mapSubscription);
    }
    @Override public Optional<Subscription> subscriptionForCurrentTenant(UUID id) {
        return tenantRow(
                "market.subscription",
                List.of("id", "tenant_id", "sku_code", "status", "started_at", "ended_at"),
                Map.of("id", id),
                this::mapSubscription);
    }
    @Override public List<CatalogItem> catalog() {
        return jdbc.sql("""
                SELECT a.id app_id,a.code,a.name,a.summary,a.created_at,
                v.id version_id,v.version,v.status,v.release_notes,v.published_at
                FROM market.app_package a JOIN market.app_version v ON v.app_id=a.id
                WHERE v.status='PUBLISHED' ORDER BY a.name,v.published_at DESC
                """).query((rs,n) -> {
            AppPackage a = new AppPackage(uuid(rs,"app_id"),rs.getString("code"),rs.getString("name"),rs.getString("summary"),instant(rs,"created_at"));
            AppVersion v = new AppVersion(uuid(rs,"version_id"),a.id(),rs.getString("version"),VersionStatus.PUBLISHED,
                    rs.getString("release_notes"),null,instant(rs,"published_at"));
            List<CatalogSku> skus = jdbc.sql("""
                    SELECT s.*, p.id pd_id, p.code pd_code, p.name pd_name, p.description pd_description,
                           p.form_template_version_id pd_form, p.process_definition_key pd_process_key,
                           p.process_definition_version pd_process_version, p.default_permission_pack::text pd_pack,
                           p.status pd_status, p.created_at pd_created, p.updated_at pd_updated, p.version pd_version
                    FROM market.sku s LEFT JOIN market.sku_product_binding b ON b.sku_code=s.code
                    LEFT JOIN market.product_definition p ON p.id=b.product_definition_id
                    WHERE s.app_id=:id ORDER BY s.code
                    """).param("id",a.id()).query((skuRow,rowNum) -> {
                Sku sku = mapSku(skuRow,rowNum);
                UUID productId = uuid(skuRow,"pd_id");
                ProductDefinition definition = productId == null ? null : mapProductDefinitionColumns(skuRow);
                return new CatalogSku(sku.code(), sku.name(), sku.entitlements(), definition);
            }).list();
            return new CatalogItem(a,v,skus);
        }).list();
    }
    @Override public void bindSkuToProductDefinition(String skuCode, UUID productDefinitionId) {
        jdbc.sql("""
                INSERT INTO market.sku_product_binding(sku_code,product_definition_id,created_at)
                VALUES(:sku,:definition,CURRENT_TIMESTAMP)
                ON CONFLICT(sku_code) DO UPDATE SET product_definition_id=:definition,created_at=CURRENT_TIMESTAMP
                """).param("sku",skuCode).param("definition",productDefinitionId).update();
    }
    @Override public Optional<ProductDefinition> productDefinitionForSku(String skuCode) {
        return jdbc.sql("""
                SELECT p.* FROM market.product_definition p JOIN market.sku_product_binding b
                  ON b.product_definition_id=p.id WHERE b.sku_code=:sku
                """).param("sku",skuCode).query(this::mapProductDefinition).optional();
    }
    @Override public List<Sku> skusBoundToProductDefinition(UUID productDefinitionId) {
        return jdbc.sql("""
                SELECT s.* FROM market.sku s JOIN market.sku_product_binding b ON b.sku_code=s.code
                WHERE b.product_definition_id=:definition ORDER BY s.code
                """).param("definition",productDefinitionId).query(this::mapSku).list();
    }
    @Override public Optional<FormTemplateVersionSnapshot> formTemplateVersionSnapshot(UUID formTemplateVersionId) {
        return jdbc.sql("""
                SELECT v.id,v.version,t.code,t.name,v.fields_snapshot::text snapshot
                FROM platform.form_template_version v JOIN platform.form_template t ON t.id=v.template_id
                WHERE v.id=:id
                """).param("id",formTemplateVersionId).query((r,n) -> new FormTemplateVersionSnapshot(uuid(r,"id"),
                        r.getString("code"),r.getString("name"),r.getInt("version"),formFields(r.getString("snapshot")))).optional();
    }
    /** Platform aggregate, like the activation review list: counts only, across tenants. */
    @Override public ProductUsageCounts productUsageCounts(UUID productDefinitionId) {
        return jdbc.sql("""
                SELECT
                  (SELECT count(*) FROM market.sku_product_binding b WHERE b.product_definition_id=:id) skus,
                  (SELECT count(*) FROM market.subscription s JOIN market.sku_product_binding b ON b.sku_code=s.sku_code
                     WHERE b.product_definition_id=:id AND s.status='ACTIVE') subscriptions,
                  (SELECT count(DISTINCT i.tenant_id) FROM market.installation_product_pin p
                     JOIN market.installation i ON i.id=p.installation_id
                     WHERE p.product_definition_id=:id AND i.status='INSTALLED') installed
                """).param("id",productDefinitionId).query((r,n) -> new ProductUsageCounts(
                        r.getLong("skus"),r.getLong("subscriptions"),r.getLong("installed"))).single();
    }
    /** Snapshot rows are the published FieldView list; tolerate snake_case keys and skip malformed entries. */
    private List<FormFieldSnapshot> formFields(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) return List.of();
        try {
            List<Map<String,Object>> rows = json.readValue(snapshot, new TypeReference<List<Map<String,Object>>>(){});
            return rows.stream().map(row -> {
                String key = text(row.getOrDefault("key", row.get("field_key")));
                if (key == null) return null;
                String label = text(row.get("label"));
                String type = text(row.getOrDefault("type", row.get("field_type")));
                return new FormFieldSnapshot(key, label == null ? key : label, type == null ? "TEXT" : type,
                        Boolean.TRUE.equals(row.get("required")));
            }).filter(java.util.Objects::nonNull).toList();
        } catch (RuntimeException malformed) {
            return List.of();
        }
    }
    private static String text(Object value) {
        return value instanceof String s && !s.isBlank() ? s : null;
    }
    @Override public void saveInstallationProductPin(InstallationProductPin pin) {
        assertCurrentTenantForInstallation(pin.installationId());
        jdbc.sql("""
                INSERT INTO market.installation_product_pin(installation_id,product_definition_id,form_template_version_id,
                    process_definition_key,process_definition_version,pinned_at)
                VALUES(:installation,:definition,:form,:processKey,:processVersion,:pinned)
                """).param("installation",pin.installationId()).param("definition",pin.productDefinitionId())
                .param("form",pin.formTemplateVersionId()).param("processKey",pin.processDefinitionKey())
                .param("processVersion",pin.processDefinitionVersion()).param("pinned",sqlTimestamp(pin.pinnedAt())).update();
    }
    @Override public Optional<InstallationProductPin> installationProductPin(UUID installationId) {
        assertCurrentTenantForInstallation(installationId);
        return jdbc.sql("""
                SELECT p.* FROM market.installation_product_pin p JOIN market.installation i ON i.id=p.installation_id
                WHERE i.id=:installation AND i.tenant_id=:tenant
                """).param("installation",installationId).param("tenant",UUID.fromString(currentTenantId()))
                .query((r,n) -> new InstallationProductPin(uuid(r,"installation_id"),uuid(r,"product_definition_id"),
                        uuid(r,"form_template_version_id"),r.getString("process_definition_key"),
                        (Integer)r.getObject("process_definition_version"),instant(r,"pinned_at"))).optional();
    }
    @Override public Optional<UUID> publishedFormTemplateVersion(UUID id) {
        return jdbc.sql("SELECT id FROM platform.form_template_version WHERE id=:id AND published_at IS NOT NULL")
                .param("id", id).query(UUID.class).optional();
    }
    @Override public void saveProductDefinition(ProductDefinition d) {
        jdbc.sql("""
                INSERT INTO market.product_definition(id,code,name,description,form_template_version_id,
                    process_definition_key,process_definition_version,default_permission_pack,status,created_at,updated_at,version)
                VALUES(:id,:code,:name,:description,:form,:processKey,:processVersion,CAST(:pack AS jsonb),:status,:created,:updated,:version)
                ON CONFLICT(id) DO UPDATE SET code=:code,name=:name,description=:description,
                    form_template_version_id=:form,process_definition_key=:processKey,
                    process_definition_version=:processVersion,default_permission_pack=CAST(:pack AS jsonb),
                    status=:status,updated_at=:updated,version=:version
                """)
                .param("id",d.id()).param("code",d.code()).param("name",d.name()).param("description",d.description())
                .param("form",d.formTemplateVersionId()).param("processKey",d.processDefinitionKey())
                .param("processVersion",d.processDefinitionVersion()).param("pack",write(d.defaultPermissionPack()))
                .param("status",d.status()).param("created",sqlTimestamp(d.createdAt())).param("updated",sqlTimestamp(d.updatedAt()))
                .param("version",d.version()).update();
    }
    @Override public List<ProductDefinition> productDefinitions() {
        return jdbc.sql("SELECT * FROM market.product_definition ORDER BY code").query(this::mapProductDefinition).list();
    }
    @Override public Optional<ProductDefinition> productDefinition(UUID id) {
        return jdbc.sql("SELECT * FROM market.product_definition WHERE id=:id").param("id",id)
                .query(this::mapProductDefinition).optional();
    }

    @Override public Optional<ProductDefinition> productDefinitionByCode(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        return jdbc.sql("SELECT * FROM market.product_definition WHERE code=:code")
                .param("code", code.trim()).query(this::mapProductDefinition).optional();
    }
    @Override public Optional<UUID> publishedFormTemplateVersionByCode(String code, int version) {
        if (code == null || code.isBlank() || version <= 0) return Optional.empty();
        return jdbc.sql("""
                SELECT v.id FROM platform.form_template_version v
                JOIN platform.form_template t ON t.id=v.template_id
                WHERE t.code=:code AND v.version=:version AND v.published_at IS NOT NULL
                """).param("code", code.trim()).param("version", version).query(UUID.class).optional();
    }
    private ProductDefinition mapProductDefinition(ResultSet r,int n)throws SQLException {
        try { return new ProductDefinition(uuid(r,"id"),r.getString("code"),r.getString("name"),r.getString("description"),
                uuid(r,"form_template_version_id"),r.getString("process_definition_key"),
                (Integer) r.getObject("process_definition_version"),
                json.readValue(r.getString("default_permission_pack"),new TypeReference<Map<String,Object>>(){}),
                r.getString("status"),instant(r,"created_at"),instant(r,"updated_at"),r.getLong("version"));
        } catch(Exception e) { throw new SQLException("invalid product definition",e); }
    }
    private ProductDefinition mapProductDefinitionColumns(ResultSet r)throws SQLException {
        try { return new ProductDefinition(uuid(r,"pd_id"),r.getString("pd_code"),r.getString("pd_name"),r.getString("pd_description"),
                uuid(r,"pd_form"),r.getString("pd_process_key"),(Integer)r.getObject("pd_process_version"),
                json.readValue(r.getString("pd_pack"),new TypeReference<Map<String,Object>>(){}),r.getString("pd_status"),
                instant(r,"pd_created"),instant(r,"pd_updated"),r.getLong("pd_version"));
        } catch(Exception e) { throw new SQLException("invalid product definition",e); }
    }
    private AppPackage mapApp(ResultSet r,int n)throws SQLException{return new AppPackage(uuid(r,"id"),r.getString("code"),r.getString("name"),r.getString("summary"),instant(r,"created_at"));}
    private AppVersion mapVersion(ResultSet r,int n)throws SQLException{return new AppVersion(uuid(r,"id"),uuid(r,"app_id"),r.getString("version"),VersionStatus.valueOf(r.getString("status")),r.getString("release_notes"),instant(r,"created_at"),instant(r,"published_at"));}
    private Sku mapSku(ResultSet r,int n)throws SQLException{try{return new Sku(r.getString("code"),uuid(r,"app_id"),r.getString("name"),json.readValue(r.getString("entitlements"),new TypeReference<Map<String,EntitlementSpec>>(){}));}catch(Exception e){throw new SQLException("invalid SKU entitlements",e);}}
    private Installation mapInstallation(ResultSet r,int n)throws SQLException{return new Installation(uuid(r,"id"),uuid(r,"tenant_id"),uuid(r,"app_id"),uuid(r,"version_id"),InstallationStatus.valueOf(r.getString("status")),instant(r,"installed_at"),instant(r,"updated_at"));}
    private Subscription mapSubscription(ResultSet r,int n)throws SQLException{return new Subscription(uuid(r,"id"),uuid(r,"tenant_id"),r.getString("sku_code"),SubscriptionStatus.valueOf(r.getString("status")),instant(r,"started_at"),instant(r,"ended_at"));}
    private <T> List<T> tenantRows(String table, List<String> projection, Map<String,Object> filters, RowMapper<T> mapper) {
        RenderedSql query = isolation.kernel().compiler()
                .compile(QueryAst.select(table, projection, filters), AccessContextHolder.current());
        return jdbc.sql(query.sql()).params(query.parameters().toArray()).query(mapper).list();
    }
    private <T> Optional<T> tenantRow(String table, List<String> projection, Map<String,Object> filters, RowMapper<T> mapper) {
        RenderedSql query = isolation.kernel().compiler()
                .compile(QueryAst.select(table, projection, filters), AccessContextHolder.current());
        return jdbc.sql(query.sql()).params(query.parameters().toArray()).query(mapper).optional();
    }
    private static void assertCurrentTenant(UUID tenantId) {
        one.nexus.isolation.api.AccessContext context = AccessContextHolder.current();
        if (!(context instanceof one.nexus.isolation.api.TenantContext tenant)
                || !tenant.tenantId().equals(tenantId.toString())) {
            throw new IllegalStateException("TenantContext does not own this market row");
        }
    }
    private void assertCurrentTenantForInstallation(UUID installationId) {
        String tenantId = currentTenantId();
        Integer count = jdbc.sql("SELECT 1 FROM market.installation WHERE id=:id AND tenant_id=:tenant")
                .param("id",installationId).param("tenant",UUID.fromString(tenantId)).query(Integer.class).optional().orElse(null);
        if (count == null) throw new IllegalStateException("installation is outside current tenant scope");
    }
    private static String currentTenantId() {
        one.nexus.isolation.api.AccessContext context=AccessContextHolder.current();
        if (!(context instanceof one.nexus.isolation.api.TenantContext tenant)) throw new IllegalStateException("TenantContext required");
        return tenant.tenantId();
    }
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private static UUID uuid(ResultSet r,String c)throws SQLException{Object v=r.getObject(c);return v==null?null:(v instanceof UUID u?u:UUID.fromString(v.toString()));}
    private static java.time.Instant instant(ResultSet r,String c)throws SQLException{var t=r.getTimestamp(c);return t==null?null:t.toInstant();}

    @Override public void saveActivationApplication(ActivationApplication a) {
        jdbc.sql("""
                INSERT INTO market.activation_application(
                  id, tenant_id, sku_code, version_id, status, review_note, reviewed_at, reviewed_by,
                  resulting_subscription_id, resulting_installation_id, created_at, created_by)
                VALUES(
                  :id, :tenant, :sku, :version, :status, :note, :reviewedAt, :reviewedBy,
                  :subId, :instId, :created, :createdBy)
                ON CONFLICT(id) DO UPDATE SET
                  status=:status, review_note=:note, reviewed_at=:reviewedAt, reviewed_by=:reviewedBy,
                  resulting_subscription_id=:subId, resulting_installation_id=:instId
                """)
                .param("id", a.id()).param("tenant", a.tenantId()).param("sku", a.skuCode())
                .param("version", a.versionId()).param("status", a.status().name())
                .param("note", a.reviewNote())
                .param("reviewedAt", sqlTimestamp(a.reviewedAt()))
                .param("reviewedBy", a.reviewedBy())
                .param("subId", a.resultingSubscriptionId())
                .param("instId", a.resultingInstallationId())
                .param("created", sqlTimestamp(a.createdAt()))
                .param("createdBy", a.createdBy())
                .update();
    }

    /** Platform-scoped list — not tenant-filtered (开通审订购). */
    @Override public List<ActivationApplication> activationApplications() {
        return jdbc.sql("""
                SELECT * FROM market.activation_application ORDER BY created_at DESC
                """).query(this::mapActivation).list();
    }

    @Override public Optional<ActivationApplication> activationApplication(UUID id) {
        return jdbc.sql("SELECT * FROM market.activation_application WHERE id=:id")
                .param("id", id).query(this::mapActivation).optional();
    }

    @Override public List<ActivationApplication> activationApplicationsForCurrentTenant() {
        return tenantRows(
                "market.activation_application",
                List.of("id", "tenant_id", "sku_code", "version_id", "status", "review_note",
                        "reviewed_at", "reviewed_by", "resulting_subscription_id",
                        "resulting_installation_id", "created_at", "created_by"),
                Map.of(),
                this::mapActivation);
    }

    @Override public boolean hasActiveSubscription(UUID tenantId, String skuCode) {
        Integer count = jdbc.sql("""
                SELECT COUNT(*) FROM market.subscription
                WHERE tenant_id=:tenant AND sku_code=:sku AND status='ACTIVE'
                """)
                .param("tenant", tenantId).param("sku", skuCode)
                .query(Integer.class).single();
        return count != null && count > 0;
    }

    private ActivationApplication mapActivation(ResultSet r, int n) throws SQLException {
        return new ActivationApplication(
                uuid(r, "id"), uuid(r, "tenant_id"), r.getString("sku_code"), uuid(r, "version_id"),
                ActivationApplicationStatus.valueOf(r.getString("status")),
                r.getString("review_note"), instant(r, "reviewed_at"),
                (UUID) r.getObject("reviewed_by"),
                (UUID) r.getObject("resulting_subscription_id"),
                (UUID) r.getObject("resulting_installation_id"),
                instant(r, "created_at"), uuid(r, "created_by"));
    }
}
