package one.nexus.p4.market.infra.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import one.nexus.p4.market.MarketRepository;
import one.nexus.p4.market.MarketModels.*;

@Repository
@DependsOn("flyway")
public class JdbcMarketRepository implements MarketRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public JdbcMarketRepository(JdbcClient jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    @Override public void saveApp(AppPackage a) {
        jdbc.sql("""
                INSERT INTO market.app_package(id,code,name,summary,created_at) VALUES(:id,:code,:name,:summary,:at)
                ON CONFLICT(id) DO UPDATE SET code=:code,name=:name,summary=:summary
                """)
                .param("id",a.id()).param("code",a.code()).param("name",a.name()).param("summary",a.summary())
                .param("at",a.createdAt()).update();
    }
    @Override public void saveVersion(AppVersion v) {
        jdbc.sql("""
                INSERT INTO market.app_version(id,app_id,version,status,release_notes,created_at,published_at)
                VALUES(:id,:app,:version,:status,:notes,:created,:published) ON CONFLICT(id) DO UPDATE SET
                status=:status,published_at=:published,release_notes=:notes
                """)
                .param("id",v.id()).param("app",v.appId()).param("version",v.version()).param("status",v.status().name())
                .param("notes",v.releaseNotes()).param("created",v.createdAt()).param("published",v.publishedAt()).update();
    }
    @Override public void saveSku(Sku s) {
        jdbc.sql("""
                INSERT INTO market.sku(code,app_id,name,entitlements) VALUES(:code,:app,:name,CAST(:items AS jsonb))
                ON CONFLICT(code) DO UPDATE SET app_id=:app,name=:name,entitlements=CAST(:items AS jsonb)
                """)
                .param("code",s.code()).param("app",s.appId()).param("name",s.name()).param("items",write(s.entitlements())).update();
    }
    @Override public void saveSubscription(Subscription s) {
        jdbc.sql("""
                INSERT INTO market.subscription(id,tenant_id,sku_code,status,started_at,ended_at)
                VALUES(:id,:tenant,:sku,:status,:started,:ended) ON CONFLICT(id) DO UPDATE SET status=:status,ended_at=:ended
                """)
                .param("id",s.id()).param("tenant",s.tenantId()).param("sku",s.skuCode()).param("status",s.status().name())
                .param("started",s.startedAt()).param("ended",s.endedAt()).update();
    }
    @Override public void saveInstallation(Installation i) {
        jdbc.sql("""
                INSERT INTO market.installation(id,tenant_id,app_id,version_id,status,installed_at,updated_at)
                VALUES(:id,:tenant,:app,:version,:status,:installed,:updated) ON CONFLICT(id) DO UPDATE SET
                version_id=:version,status=:status,updated_at=:updated
                """)
                .param("id",i.id()).param("tenant",i.tenantId()).param("app",i.appId()).param("version",i.versionId())
                .param("status",i.status().name()).param("installed",i.installedAt()).param("updated",i.updatedAt()).update();
    }
    @Override public Optional<AppPackage> app(UUID id) { return jdbc.sql("SELECT * FROM market.app_package WHERE id=:id").param("id",id).query(this::mapApp).optional(); }
    @Override public Optional<AppVersion> version(UUID id) { return jdbc.sql("SELECT * FROM market.app_version WHERE id=:id").param("id",id).query(this::mapVersion).optional(); }
    @Override public Optional<Sku> sku(String code) { return jdbc.sql("SELECT * FROM market.sku WHERE code=:code").param("code",code).query(this::mapSku).optional(); }
    @Override public Optional<Installation> installation(UUID id) { return jdbc.sql("SELECT * FROM market.installation WHERE id=:id").param("id",id).query(this::mapInstallation).optional(); }
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
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private static UUID uuid(ResultSet r,String c)throws SQLException{Object v=r.getObject(c);return v==null?null:(v instanceof UUID u?u:UUID.fromString(v.toString()));}
    private static java.time.Instant instant(ResultSet r,String c)throws SQLException{var t=r.getTimestamp(c);return t==null?null:t.toInstant();}
}
