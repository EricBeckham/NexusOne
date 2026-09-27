package one.nexus.p4.market.infra.jdbc;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p4.market.InstallationProcessConfigurationRepository;
import one.nexus.p2.kernel.TenantContextHolder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
@Repository
public class JdbcInstallationProcessConfigurationRepository implements InstallationProcessConfigurationRepository {
    private final JdbcClient jdbc;
    public JdbcInstallationProcessConfigurationRepository(JdbcClient jdbc) { this.jdbc = jdbc; }
    private static void own(UUID tenant) {
        if (!TenantContextHolder.require().tenantId().equals(tenant)) throw new SecurityException("process override tenant mismatch");
    }
    public Optional<Override> find(UUID tenant, UUID installation) {
        own(tenant);
        return jdbc.sql("SELECT process_definition_key,process_definition_version FROM market.installation_process_override WHERE tenant_id=:tenant AND installation_id=:installation")
                .param("tenant", tenant).param("installation", installation).query((rs,n) -> new Override(rs.getString(1),rs.getInt(2))).optional();
    }
    public void save(UUID tenant, UUID installation, String key, int version, UUID person, Instant now) {
        own(tenant);
        int changed = jdbc.sql("""
                INSERT INTO market.installation_process_override(installation_id,tenant_id,process_definition_key,process_definition_version,updated_by,updated_at)
                VALUES(:installation,:tenant,:key,:version,:person,:now)
                ON CONFLICT(installation_id) DO UPDATE SET process_definition_key=:key,process_definition_version=:version,updated_by=:person,updated_at=:now
                WHERE market.installation_process_override.tenant_id=:tenant
                """).param("installation", installation).param("tenant",tenant).param("key",key).param("version",version)
                .param("person",person).param("now",java.sql.Timestamp.from(now)).update();
        if (changed != 1) throw new SecurityException("process override is outside tenant scope");
    }
    public void delete(UUID tenant, UUID installation) {
        own(tenant);
        jdbc.sql("DELETE FROM market.installation_process_override WHERE tenant_id=:tenant AND installation_id=:installation")
                .param("tenant",tenant).param("installation",installation).update();
    }
}
