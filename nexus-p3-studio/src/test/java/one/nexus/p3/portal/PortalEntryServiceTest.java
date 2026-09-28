package one.nexus.p3.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p3.instance.ProcessMappingService;
import org.junit.jupiter.api.Test;

class PortalEntryServiceTest {
    private final UUID tenant = UUID.fromString("01900000-0000-7000-8000-000000000101");

    @Test
    void openFilterShowsEveryInstalledEntryInGroupThenSortOrder() {
        PortalEntryService portal = new PortalEntryService(source(
                entry("fn:leave:leave_approve", "leave", "leave_approve", "审批", EntryKind.VIEW, "b", 20),
                entry("fn:leave:leave_list", "leave", "leave_list", "列表", EntryKind.LIST, "a", 30),
                entry("fn:leave:leave_apply", "leave", "leave_apply", "填报", EntryKind.FILL, "a", 10),
                entry("fn:leave:broken", "leave", "leave_apply", "坏入口", EntryKind.LIST, "a", 1)
        ), new OpenEntryReadFilter(), (tenantId, processKey) -> Optional.empty());
        List<PortalEntry> visible = portal.visible(tenant);
        assertEquals(List.of("fn:leave:leave_apply", "fn:leave:leave_list", "fn:leave:leave_approve"),
                visible.stream().map(PortalEntry::key).toList());
    }

    @Test
    void readableFilterHidesEntriesTheCallerCannotSee() {
        PortalEntryService portal = new PortalEntryService(source(
                entry("fn:leave:leave_apply", "leave", "leave_apply", "填报", EntryKind.FILL, "填报", 10),
                entry("fn:leave:leave_approve", "leave", "leave_approve", "审批", EntryKind.VIEW, "审批", 20)
        ), keys -> keys.stream().filter(key -> key.endsWith("leave_apply")).toList(),
                (tenantId, processKey) -> Optional.empty());
        List<PortalEntry> visible = portal.visible(tenant);
        assertEquals(List.of("fn:leave:leave_apply"), visible.stream().map(PortalEntry::key).toList());
    }

    @Test
    void emptyInstallShowsNothing() {
        PortalEntryService portal = new PortalEntryService(tenantId -> List.of(), new OpenEntryReadFilter(),
                (tenantId, processKey) -> Optional.empty());
        assertTrue(portal.visible(tenant).isEmpty());
    }

    private static InstalledEntrySource source(FunctionEntry... entries) {
        List<FunctionEntry> rows = List.of(entries);
        return tenantId -> rows;
    }

    private static FunctionEntry entry(String key, String app, String form, String name, EntryKind kind, String group, int sort) {
        return new FunctionEntry(key, app, form, "leave", name, "calendar", kind, group, sort, null);
    }

    @Test
    void fillEntryStaysVisibleWhenItsProcessNeedsAPosition() {
        PortalEntryService portal = new PortalEntryService(source(
                new FunctionEntry("fn:leave:leave_apply", "leave", "leave_apply", "leave", "请假申请", "calendar",
                        EntryKind.FILL, "办公", 10, "leave_approval"),
                new FunctionEntry("fn:leave:leave_view", "leave", "leave_view", "leave", "请假记录", "calendar",
                        EntryKind.LIST, "办公", 20, null)
        ), new OpenEntryReadFilter(),
                (tenantId, processKey) -> "leave_approval".equals(processKey)
                        ? Optional.of(ProcessMappingService.BLOCKED_MESSAGE) : Optional.empty());
        List<PortalEntry> visible = portal.visible(tenant);
        assertEquals(2, visible.size());
        assertEquals(false, visible.get(0).submittable());
        assertEquals(ProcessMappingService.BLOCKED_MESSAGE, visible.get(0).blockedReason());
        assertEquals(true, visible.get(1).submittable());
    }
}
