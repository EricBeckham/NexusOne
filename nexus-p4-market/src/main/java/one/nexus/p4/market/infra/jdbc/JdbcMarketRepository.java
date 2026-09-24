package one.nexus.p4.market.infra.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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
                .param("at",timestamp(a.createdAt())).update();
    }
    @Override public void saveVersion(AppVersion v) {
        jdbc.sql("""
                INSERT INTO market.app_version(id,app_id,version,status,release_notes,created_at,published_at)
                VALUES(:id,:app,:version,:status,:notes,:created,:published) ON CONFLICT(id) DO UPDATE SET
                status=:status,published_at=:published,release_notes=:notes
                """)
                .param("id",v.id()).param("app",v.appId()).param("version",v.version()).param("status",v.status().name())
                .param("notes",v.releaseNotes()).param("created",timestamp(v.createdAt()))
                .param("published",timestamp(v.publishedAt())).update();
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
                .param("started",timestamp(s.startedAt())).param("ended",timestamp(s.endedAt())).update();
    }
    @Override public void saveInstallation(Installation i) {
        assertCurrentTenant(i.tenantId());
        jdbc.sql("""
                INSERT INTO market.installation(id,tenant_id,app_id,version_id,status,installed_at,updated_at)
                VALUES(:id,:tenant,:app,:version,:status,:installed,:updated) ON CONFLICT(id) DO UPDATE SET
                version_id=:version,status=:status,updated_at=:updated
                """)
                .param("id",i.id()).param("tenant",i.tenantId()).param("app",i.appId()).param("version",i.versionId())
                .param("status",i.status().name()).param("installed",timestamp(i.installedAt()))
                .param("updated",timestamp(i.updatedAt())).update();
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
            List<Sku> skus = jdbc.sql("SELECT * FROM market.sku WHERE app_id=:id ORDER BY code").param("id",a.id()).query(this::mapSku).list();
            return new CatalogItem(a,v,skus);
        }).list();
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
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private static UUID uuid(ResultSet r,String c)throws SQLException{Object v=r.getObject(c);return v==null?null:(v instanceof UUID u?u:UUID.fromString(v.toString()));}
    private static java.time.Instant instant(ResultSet r,String c)throws SQLException{var t=r.getTimestamp(c);return t==null?null:t.toInstant();}
    private static Timestamp timestamp(java.time.Instant value){return value==null?null:Timestamp.from(value);}
}
