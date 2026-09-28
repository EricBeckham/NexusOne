package one.nexus.p4.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p3.instance.ProcessPositionSource.PositionRef;
import one.nexus.p3.template.PlatformTemplateCatalog;
import one.nexus.p3.template.PlatformTemplateCatalog.TemplateVersion;
import one.nexus.p4.market.MarketProcessPositionSource.InstalledVersion;
import org.junit.jupiter.api.Test;

class MarketProcessPositionSourceTest {
    private final UUID tenant = UUID.fromString("01900000-0000-7000-8000-000000000101");
    private final UUID instance = UUID.fromString("01900000-0000-7000-8000-000000000201");
    private final UUID versionId = UUID.fromString("01900000-0000-7000-8000-000000000301");

    @Test
    void listsPositionAssigneesAndSkipsRoles() {
        MarketProcessPositionSource source = source(List.of(new InstalledVersion(instance, versionId)));

        assertEquals(List.of(new PositionRef(instance, "leave_approval", "pl_lead")), source.positions(tenant));
    }

    @Test
    void staysEmptyForAnotherTenantOrAnUnpublishedVersion() {
        MarketProcessPositionSource otherTenant = source(List.of());
        MarketProcessPositionSource unpublished = source(List.of(new InstalledVersion(instance, UUID.randomUUID())));

        assertTrue(otherTenant.positions(UUID.randomUUID()).isEmpty());
        assertTrue(unpublished.positions(tenant).isEmpty());
        assertTrue(source(List.of(new InstalledVersion(instance, versionId))).positions(null).isEmpty());
    }

    private MarketProcessPositionSource source(List<InstalledVersion> rows) {
        TemplateVersion template = new TemplateVersion(versionId, "leave", "1.0.0", "oa", List.of(), List.of(),
                List.of(Map.of(
                        "key", "leave_approval",
                        "nodes", List.of(
                                Map.of("assignee", Map.of("type", "ROLE", "ref", "leave_approver")),
                                Map.of("assignee", Map.of("type", "POSITION", "ref", "pl_lead")),
                                Map.of("assignee", Map.of("type", "POSITION", "ref", "Not A Code"))))),
                List.of(), List.of());
        PlatformTemplateCatalog catalog = id -> versionId.equals(id) ? Optional.of(template) : Optional.empty();
        return new MarketProcessPositionSource(catalog, tenantId -> tenant.equals(tenantId) ? rows : List.of());
    }
}
