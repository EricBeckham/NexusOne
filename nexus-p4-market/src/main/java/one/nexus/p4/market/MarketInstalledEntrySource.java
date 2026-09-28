package one.nexus.p4.market;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p3.portal.EntryKind;
import one.nexus.p3.portal.FunctionEntry;
import one.nexus.p3.portal.InstalledEntrySource;
import one.nexus.p3.template.PlatformTemplateCatalog;
import one.nexus.p3.template.PlatformTemplateCatalog.TemplateVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * PLB7. Function entries from the current tenant's installed, published manifests.
 * Registering this bean retires {@code EmptyInstalledEntrySource}.
 */
@Component
public class MarketInstalledEntrySource implements InstalledEntrySource {
    private final PlatformTemplateCatalog catalog;
    private final InstalledVersions installed;

    @Autowired // two constructors: tell Spring which one (the other is for tests)

    public MarketInstalledEntrySource(JdbcClient jdbc, PlatformTemplateCatalog catalog) {
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

    MarketInstalledEntrySource(PlatformTemplateCatalog catalog, InstalledVersions installed) {
        this.catalog = catalog;
        this.installed = installed;
    }

    @Override
    public List<FunctionEntry> installed(UUID tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        List<FunctionEntry> entries = new ArrayList<>();
        for (InstalledVersion row : installed.list(tenantId)) {
            catalog.published(row.versionId()).ifPresent(template -> entries.addAll(entries(template)));
        }
        return List.copyOf(entries);
    }

    static List<FunctionEntry> entries(TemplateVersion template) {
        List<FunctionEntry> entries = new ArrayList<>();
        for (Map<String, Object> row : template.entries()) {
            entry(row).ifPresent(entries::add);
        }
        return entries;
    }

    static Optional<FunctionEntry> entry(Map<String, Object> row) {
        if (row == null) {
            return Optional.empty();
        }
        String key = text(row.get("key"));
        String app = text(row.get("app_code"));
        String form = text(row.get("form_code"));
        String ledger = text(row.get("ledger_code"));
        String name = text(row.get("name"));
        EntryKind kind = kind(row.get("kind"));
        if (key == null || app == null || form == null || ledger == null || name == null || kind == null) {
            return Optional.empty();
        }
        int sort = row.get("sort") instanceof Number number ? number.intValue() : 0;
        return Optional.of(new FunctionEntry(key, app, form, ledger, name, text(row.get("icon")), kind,
                text(row.get("default_group")), sort, text(row.get("process_key"))));
    }

    private static EntryKind kind(Object value) {
        if (!(value instanceof String name) || name.isBlank()) {
            return null;
        }
        try {
            return EntryKind.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String text(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        return text;
    }

    record InstalledVersion(UUID instanceId, UUID versionId) {}

    @FunctionalInterface
    interface InstalledVersions {
        List<InstalledVersion> list(UUID tenantId);
    }
}
