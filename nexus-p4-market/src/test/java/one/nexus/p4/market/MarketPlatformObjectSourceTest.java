package one.nexus.p4.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p3.instance.ObjectType;
import one.nexus.p3.instance.PlatformObject;
import one.nexus.p3.template.PlatformTemplateCatalog;
import one.nexus.p3.template.PlatformTemplateCatalog.TemplateVersion;
import org.junit.jupiter.api.Test;

class MarketPlatformObjectSourceTest {
    private final UUID tenant = UUID.fromString("01900000-0000-7000-8000-000000000101");
    private final UUID instance = UUID.fromString("01900000-0000-7000-8000-000000000201");
    private final UUID versionId = UUID.fromString("01900000-0000-7000-8000-000000000301");

    @Test
    void readsFormProcessAndRoleFromThePinnedManifest() {
        MarketPlatformObjectSource source = source(versionId);

        PlatformObject form = source.find(tenant, instance, ObjectType.FORM, "leave_apply").orElseThrow();
        PlatformObject process = source.find(tenant, instance, ObjectType.PROCESS, "leave_approval").orElseThrow();
        PlatformObject role = source.find(tenant, instance, ObjectType.ROLE, "leave_applicant").orElseThrow();

        assertEquals("1.0.0", form.version());
        assertEquals("请假申请", form.content().get("name"));
        assertEquals("leave_approval", process.content().get("key"));
        assertEquals("请假申请人", role.content().get("name"));
    }

    @Test
    void hidesAnotherTenantsInstanceAndAMissingObject() {
        MarketPlatformObjectSource source = source(versionId);

        assertTrue(source.find(UUID.randomUUID(), instance, ObjectType.FORM, "leave_apply").isEmpty());
        assertTrue(source.find(tenant, instance, ObjectType.FORM, "missing").isEmpty());
        assertTrue(source.find(tenant, instance, ObjectType.PROCESS, "leave_apply").isEmpty());
    }

    @Test
    void staysEmptyWhenThePinnedVersionIsNotPublished() {
        MarketPlatformObjectSource source = source(null);

        assertTrue(source.find(tenant, instance, ObjectType.FORM, "leave_apply").isEmpty());
    }

    private MarketPlatformObjectSource source(UUID publishedId) {
        TemplateVersion template = new TemplateVersion(versionId, "leave", "1.0.0", "oa",
                List.of(),
                List.of(Map.of("code", "leave_apply", "name", "请假申请")),
                List.of(Map.of("key", "leave_approval", "name", "请假审批")),
                List.of(Map.of("code", "leave_applicant", "name", "请假申请人")),
                List.of());
        PlatformTemplateCatalog catalog = id -> publishedId != null && publishedId.equals(id)
                ? Optional.of(template) : Optional.empty();
        return new MarketPlatformObjectSource(catalog,
                (tenantId, instanceId) -> tenant.equals(tenantId) && instance.equals(instanceId)
                        ? Optional.of(versionId) : Optional.empty());
    }
}
