package one.nexus.p4.market;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p3.template.PlatformTemplateCatalog;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Read-only implementation of the P3 template port over PLB1's published market snapshot. */
@Repository
@DependsOn("flyway")
public class PublishedPlatformTemplateCatalog implements PlatformTemplateCatalog {
    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public PublishedPlatformTemplateCatalog(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TemplateVersion> published(UUID versionId) {
        return jdbc.sql("""
                SELECT v.version, v.manifest, a.code AS app_code
                FROM market.app_version v JOIN market.app_package a ON a.id=v.app_id
                WHERE v.id=:id AND v.status IN ('PUBLISHED','UNPUBLISHED') AND v.manifest IS NOT NULL
                """).param("id", versionId).query((row, index) -> {
                    Map<String, Object> manifest = json.readValue(row.getString("manifest"),
                            new TypeReference<Map<String, Object>>() {});
                    return new TemplateVersion(versionId, row.getString("app_code"), row.getString("version"),
                            (String) manifest.get("category_code"), section(manifest, "ledgers"), section(manifest, "forms"),
                            section(manifest, "processes"), section(manifest, "role_pack"), section(manifest, "entries"));
                }).optional();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> section(Map<String, Object> manifest, String name) {
        Object value = manifest.get(name);
        if (!(value instanceof List<?> list) || list.stream().anyMatch(item -> !(item instanceof Map<?, ?>)))
            throw new IllegalStateException("published application manifest has invalid " + name);
        return (List<Map<String, Object>>) value;
    }
}
