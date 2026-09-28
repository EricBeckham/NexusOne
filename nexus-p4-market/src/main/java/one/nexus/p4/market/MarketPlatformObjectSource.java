package one.nexus.p4.market;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p3.instance.ObjectType;
import one.nexus.p3.instance.PlatformObject;
import one.nexus.p3.instance.PlatformObjectSource;
import one.nexus.p3.template.PlatformTemplateCatalog;
import one.nexus.p3.template.PlatformTemplateCatalog.TemplateVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * PLB3. Reads one object from the instance's pinned published manifest. A row for another tenant is not returned.
 * Registering this bean retires {@code EmptyPlatformObjectSource}.
 */
@Component
public class MarketPlatformObjectSource implements PlatformObjectSource {
    private final PlatformTemplateCatalog catalog;
    private final VersionIds versions;

    @Autowired // two constructors: tell Spring which one (the other is for tests)

    public MarketPlatformObjectSource(JdbcClient jdbc, PlatformTemplateCatalog catalog) {
        this(catalog, (tenantId, instanceId) -> jdbc.sql("""
                SELECT app_version_id FROM market.app_instance
                WHERE id = :id AND tenant_id = :tenant
                """).param("id", instanceId).param("tenant", tenantId)
                .query((row, index) -> row.getObject("app_version_id", UUID.class))
                .optional());
    }

    MarketPlatformObjectSource(PlatformTemplateCatalog catalog, VersionIds versions) {
        this.catalog = catalog;
        this.versions = versions;
    }

    @Override
    public Optional<PlatformObject> find(UUID tenantId, UUID instanceId, ObjectType type, String code) {
        if (tenantId == null || instanceId == null || type == null || code == null || code.isBlank()) {
            return Optional.empty();
        }
        return versions.find(tenantId, instanceId)
                .flatMap(catalog::published)
                .flatMap(template -> object(template, type, code));
    }

    static Optional<PlatformObject> object(TemplateVersion template, ObjectType type, String code) {
        String field = type == ObjectType.PROCESS ? "key" : "code";
        List<Map<String, Object>> section = switch (type) {
            case FORM -> template.forms();
            case PROCESS -> template.processes();
            case ROLE -> template.rolePack();
        };
        return section.stream()
                .filter(row -> row != null && code.equals(row.get(field)))
                .findFirst()
                .map(row -> new PlatformObject(template.version(), new LinkedHashMap<>(row)));
    }

    @FunctionalInterface
    interface VersionIds {
        Optional<UUID> find(UUID tenantId, UUID instanceId);
    }
}
