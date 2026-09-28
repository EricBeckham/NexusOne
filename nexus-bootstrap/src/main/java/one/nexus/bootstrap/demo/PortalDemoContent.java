package one.nexus.bootstrap.demo;

import java.util.List;
import java.util.UUID;
import one.nexus.p3.portal.HomeModule;
import one.nexus.p3.portal.MenuItem;
import one.nexus.p3.portal.PortalConfigService;

/** PLC9. Demo portal rows for the fixed P1 tenant, written only through the PLC3 service. */
public final class PortalDemoContent {
    public static final UUID GROUP_OFFICE = UUID.fromString("01900000-0000-7000-8000-000000000901");
    public static final UUID ENTRY_LEAVE = UUID.fromString("01900000-0000-7000-8000-000000000902");
    public static final UUID ENTRY_EXPENSE = UUID.fromString("01900000-0000-7000-8000-000000000903");

    private PortalDemoContent() {}

    public static void write(PortalConfigService config, UUID tenantId) {
        config.replaceMenu(tenantId, List.of(
                new MenuItem(GROUP_OFFICE, null, "办公", null, "办事", 0, false),
                new MenuItem(ENTRY_LEAVE, GROUP_OFFICE, "请假申请", "fn:leave:leave_apply", null, 0, false),
                new MenuItem(ENTRY_EXPENSE, GROUP_OFFICE, "报销", "fn:expense:expense_apply", null, 1, false)));
        config.replaceModules(tenantId, List.of(
                new HomeModule("todos", "待办", 0, false),
                new HomeModule("notices", "通知", 1, false)));
    }
}
