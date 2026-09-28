package one.nexus.p4.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p3.portal.EntryKind;
import one.nexus.p3.portal.FunctionEntry;
import one.nexus.p3.template.PlatformTemplateCatalog;
import one.nexus.p3.template.PlatformTemplateCatalog.TemplateVersion;
import one.nexus.p4.market.MarketInstalledEntrySource.InstalledVersion;
import org.junit.jupiter.api.Test;

class MarketInstalledEntrySourceTest {
    private final UUID tenant = UUID.fromString("01900000-0000-7000-8000-000000000101");
    private final UUID instance = UUID.fromString("01900000-0000-7000-8000-000000000201");
    private final UUID versionId = UUID.fromString("01900000-0000-7000-8000-000000000301");

    @Test
    void listsInstalledManifestEntries() {
        MarketInstalledEntrySource source = source(List.of(new InstalledVersion(instance, versionId)));

        List<FunctionEntry> entries = source.installed(tenant);

        assertEquals(List.of("fn:leave:leave_apply", "fn:leave:leave_view"),
                entries.stream().map(FunctionEntry::key).toList());
        assertEquals("请假申请", entries.get(0).name());
        assertEquals(EntryKind.FILL, entries.get(0).kind());
        assertEquals("leave_approval", entries.get(0).processKey());
        assertEquals("办公", entries.get(0).defaultGroup());
        assertEquals(10, entries.get(0).sort());
        assertEquals(EntryKind.LIST, entries.get(1).kind());
        assertNull(entries.get(1).processKey());
    }

    @Test
    void staysEmptyForAnotherTenantOrAnUnpublishedVersion() {
        assertTrue(source(List.of()).installed(UUID.randomUUID()).isEmpty());
        assertTrue(source(List.of(new InstalledVersion(instance, UUID.randomUUID()))).installed(tenant).isEmpty());
        assertTrue(source(List.of(new InstalledVersion(instance, versionId))).installed(null).isEmpty());
    }

    private MarketInstalledEntrySource source(List<InstalledVersion> rows) {
        TemplateVersion template = new TemplateVersion(versionId, "leave", "1.0.0", "oa", List.of(), List.of(),
                List.of(), List.of(), List.of(
                        Map.of("key", "fn:leave:leave_apply", "app_code", "leave", "form_code", "leave_apply",
                                "ledger_code", "leave", "name", "请假申请", "icon", "calendar", "kind", "FILL",
                                "default_group", "办公", "sort", 10, "process_key", "leave_approval"),
                        Map.of("key", "fn:leave:leave_view", "app_code", "leave", "form_code", "leave_view",
                                "ledger_code", "leave", "name", "请假记录", "icon", "list", "kind", "LIST",
                                "default_group", "办公", "sort", 20),
                        Map.of("key", "fn:leave:broken", "kind", "NOPE")));
        PlatformTemplateCatalog catalog = id -> versionId.equals(id) ? Optional.of(template) : Optional.empty();
        return new MarketInstalledEntrySource(catalog, tenantId -> tenant.equals(tenantId) ? rows : List.of());
    }
}
