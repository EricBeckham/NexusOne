package one.nexus.p4.market;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import one.nexus.p3.instance.ProcessPositionSource;
import one.nexus.p3.template.PlatformTemplateCatalog;
import one.nexus.p3.template.PlatformTemplateCatalog.TemplateVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * PLB3. POSITION assignees on the current tenant's installed, published manifests.
 * ROLE assignees are ignored. Registering this bean retires {@code EmptyProcessPositionSource}.
 */
@Component
public class MarketProcessPositionSource implements ProcessPositionSource {
    private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9_]{0,23}$");

    private final PlatformTemplateCatalog catalog;
    private final InstalledVersions installed;

    @Autowired // two constructors: tell Spring which one (the other is for tests)

    public MarketProcessPositionSource(JdbcClient jdbc, PlatformTemplateCatalog catalog) {
        this(catalog, tenantId -> jdbc.sql("""
                SELECT id, app_version_id FROM market.app_instance
                WHERE tenant_id = :tenant AND status = 'INSTALLED'
                ORDER BY id
                """).param("tenant", tenantId)
                .query((row, index) -> new InstalledVersion(
                        row.getObject("id", UUID.class),
                        row.getObject("app_version_id", UUID.class)))
                .list());
    }

    MarketProcessPositionSource(PlatformTemplateCatalog catalog, InstalledVersions installed) {
        this.catalog = catalog;
        this.installed = installed;
    }

    @Override
    public List<PositionRef> positions(UUID tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        List<PositionRef> refs = new ArrayList<>();
        for (InstalledVersion row : installed.list(tenantId)) {
            catalog.published(row.versionId()).ifPresent(template -> refs.addAll(refs(row.instanceId(), template)));
        }
        return List.copyOf(refs);
    }

    static List<PositionRef> refs(UUID instanceId, TemplateVersion template) {
        List<PositionRef> refs = new ArrayList<>();
        for (Map<String, Object> process : template.processes()) {
            if (!(process.get("key") instanceof String processKey) || processKey.isBlank()) {
                continue;
            }
            if (!(process.get("nodes") instanceof List<?> nodes)) {
                continue;
            }
            for (Object node : nodes) {
                if (!(node instanceof Map<?, ?> map)) {
                    continue;
                }
                if (!(map.get("assignee") instanceof Map<?, ?> assignee) || !"POSITION".equals(assignee.get("type"))) {
                    continue;
                }
                if (!(assignee.get("ref") instanceof String code) || !CODE.matcher(code).matches()) {
                    continue;
                }
                PositionRef ref = new PositionRef(instanceId, processKey, code);
                if (!refs.contains(ref)) {
                    refs.add(ref);
                }
            }
        }
        return refs;
    }

    record InstalledVersion(UUID instanceId, UUID versionId) {}

    @FunctionalInterface
    interface InstalledVersions {
        List<InstalledVersion> list(UUID tenantId);
    }
}
